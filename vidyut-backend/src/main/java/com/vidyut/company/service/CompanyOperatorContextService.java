package com.vidyut.company.service;

import com.vidyut.autopilot.entity.*;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.booking.entity.*;
import com.vidyut.land.entity.*;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.repository.InstallationRequestRepository;
import com.vidyut.marketplace.repository.InstallationProposalRepository;
import com.vidyut.station.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only evidence for the operator assistant. No model-computed metrics or mutations. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyOperatorContextService {
    private final AutopilotStopRepository stopRepository;
    private final AutopilotTripRepository tripRepository;
    private final LandListingRepository propertyRepository;
    private final InstallationRequestRepository requestRepository;
    private final InstallationProposalRepository proposalRepository;

    public Map<String, Object> inspect(Long accountId, String question, List<ChargingStation> stations,
                                       List<Booking> bookings) {
        String q = question.toLowerCase(Locale.ROOT).replaceAll("\\bagar\\b", "agra");
        List<Long> ids = stations.stream().map(ChargingStation::getId).toList();
        List<AutopilotStop> stops = ids.isEmpty() ? List.of()
                : stopRepository.findByStationIdInAndStatus(ids, AutopilotStopStatus.RESERVED);
        Set<Long> activeTrips = tripRepository.findAllById(stops.stream().map(AutopilotStop::getTripId).distinct().toList())
                .stream().filter(t -> t.getStatus() != AutopilotTripStatus.COMPLETED && t.getStatus() != AutopilotTripStatus.CANCELLED)
                .map(AutopilotTrip::getId).collect(Collectors.toSet());
        stops = stops.stream().filter(s -> activeTrips.contains(s.getTripId())).toList();
        final List<AutopilotStop> activeStops = stops;
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, LandListing> linkedProperties = new LinkedHashMap<>();
        Map<Long, Long> stationPropertyIds = new HashMap<>();
        boolean namedStation = stations.stream().anyMatch(s -> mentions(q, s));
        for (ChargingStation station : stations) {
            if (namedStation && !mentions(q, station)) continue;
            if ((q.contains("host-partnered") || q.contains("host partnered"))
                    && station.getOwnershipType() != StationOwnershipType.HOST_PARTNERED) continue;
            LandListing property = resolveProperty(accountId, station).orElse(null);
            if (property != null) { linkedProperties.put(property.getId(), property); stationPropertyIds.put(station.getId(), property.getId()); }
            List<ChargingConnector> connectors = station.getConnectors();
            List<ChargingConnector> issues = connectors.stream().filter(c -> severity(c) > 0).toList();
            List<AutopilotStop> journeyStops = activeStops.stream().filter(s -> s.getStationId().equals(station.getId())).toList();
            List<Long> affectedJourneys = journeyStops.stream().filter(s -> connectors.stream().anyMatch(c ->
                            severity(c) > 0 && (s.getConnectorId() != null ? s.getConnectorId().equals(c.getId())
                                    : s.getConnectorType().equalsIgnoreCase(c.getType().name()) && connectors.stream().noneMatch(b ->
                                            online(b) && b.getType() == c.getType()))))
                    .map(AutopilotStop::getTripId).distinct().toList();
            List<Long> stationBookings = bookings.stream().filter(b -> Objects.equals(b.getStationId(), station.getId()))
                    .filter(CompanyOperatorContextService::activeBooking).map(Booking::getId).toList();
            long unavailable = connectors.stream().filter(c -> !online(c)).count();
            int severity = issues.stream().mapToInt(CompanyOperatorContextService::severity).max().orElse(0);
            Long downtime = issues.stream().filter(c -> c.getStatusChangedAt() != null)
                    .mapToLong(c -> Math.max(0, Duration.between(c.getStatusChangedAt(), LocalDateTime.now()).toMinutes()))
                    .max().stream().boxed().findFirst().orElse(null);
            Map<String, Object> row = data("stationId", station.getId(), "stationName", station.getName(), "city", station.getCity(),
                    "ownershipType", station.getOwnershipType(), "hostName", station.getPropertyOwnerName(),
                    "propertyId", property == null ? null : property.getId(), "hostPartnershipId", station.getHostPartnershipId(), "operator", station.getOperatorCompanyName(),
                    "status", station.getStatus(), "demoData", station.isDemoData(), "connectors", connectors.size(),
                    "unavailableConnectors", unavailable, "faultedConnectors", connectors.stream().filter(c -> c.getStatus() == ChargerStatus.FAULT).count(),
                    "maintenanceConnectors", connectors.stream().filter(c -> c.getStatus() == ChargerStatus.MAINTENANCE || c.isMaintenanceMode()).count(),
                    "issueCount", issues.size(), "severity", severity, "downtimeMinutes", downtime,
                    "affectedJourneyIds", affectedJourneys, "activeJourneyIds", journeyStops.stream().map(AutopilotStop::getTripId).distinct().toList(),
                    "activeBookingIds", stationBookings, "bookingsScope", "Station-level reservations; not all are assigned to a specific connector",
                    "occupancyPercent", station.getOccupancyPercent(), "queueCount", station.getQueueCount(),
                    "pricePerKwh", station.getPricePerKwh(), "ccs2Connectors", connectors.stream().filter(c -> c.getType() == ConnectorType.CCS2).count(),
                    "availableCcs2", connectors.stream().filter(c -> c.getType() == ConnectorType.CCS2 && online(c)).count(),
                    "acOnly", !connectors.isEmpty() && connectors.stream().allMatch(c -> c.getType() == ConnectorType.TYPE2),
                    "chargerDetails", connectors.stream().map(c -> data("chargerId", c.getId(), "chargerCode", c.getChargerCode(),
                            "connectorType", c.getType(), "powerKw", c.getPowerKw(), "operationalStatus", c.getStatus(),
                            "available", c.isAvailable(), "maintenanceMode", c.isMaintenanceMode(), "healthScore", c.getHealthScore(),
                            "faultCode", c.getFaultCode(), "faultReason", c.getFaultReason(), "source", c.getStatusSource(),
                            "lastHeartbeat", c.getLastHeartbeat(), "statusChangedAt", c.getStatusChangedAt(),
                            "plannedJourneyIds", journeyStops.stream().filter(s -> Objects.equals(s.getConnectorId(), c.getId()))
                                    .map(AutopilotStop::getTripId).distinct().toList())).toList());
            if (property != null) row.put("propertyTitle", property.getTitle());
            rows.add(row);
        }
        rows.sort(Comparator.<Map<String, Object>>comparingInt(r -> ((Number) r.get("severity")).intValue()).reversed()
                .thenComparing(Comparator.comparingInt((Map<String, Object> r) -> ((List<?>) r.get("affectedJourneyIds")).size()).reversed())
                .thenComparing(Comparator.comparingInt((Map<String, Object> r) -> ((List<?>) r.get("activeBookingIds")).size()).reversed())
                .thenComparing(Comparator.comparingLong((Map<String, Object> r) -> ((Number) r.get("unavailableConnectors")).longValue()).reversed())
                .thenComparing(Comparator.comparingLong((Map<String, Object> r) -> r.get("downtimeMinutes") == null ? 0 : ((Number) r.get("downtimeMinutes")).longValue()).reversed())
                .thenComparing(Comparator.comparingDouble((Map<String, Object> r) -> ((Number) r.get("occupancyPercent")).doubleValue()).reversed()));
        List<Map<String, Object>> priorities = rows.stream().filter(r -> ((Number) r.get("issueCount")).intValue() > 0).toList();
        propertyRepository.findByDiscoverableTrueAndStatusIn(List.of(LandListingStatus.APPROVED, LandListingStatus.ACTIVE))
                .forEach(p -> linkedProperties.putIfAbsent(p.getId(), p));
        List<Map<String, Object>> properties = linkedProperties.values().stream()
                .map(p -> data("propertyId", p.getId(), "title", p.getTitle(), "hostAccountId", p.getHostUserId(),
                        "city", p.getCity(), "state", p.getState(), "address", p.getAddress(),
                        "latitude", p.getLatitude(), "longitude", p.getLongitude(), "parkingBays", p.getAvailableParkingBays(),
                        "availableLoadKw", p.getAvailableLoadKw(), "powerPhase", p.getPowerPhase(),
                        "operatingHours", p.getOperatingHours(), "status", p.getStatus(), "readinessScore", p.getPropertyScore(),
                        "preferredConnector", p.getPreferredConnectorType(), "partneredStationIds", stations.stream()
                                .filter(s -> Objects.equals(stationPropertyIds.get(s.getId()), p.getId()))
                                .map(ChargingStation::getId).toList())).toList();
        List<Map<String, Object>> offers = requestRepository.findByCompany_Account_IdOrderByUpdatedAtDesc(accountId).stream()
                .filter(r -> !q.contains("agra") || lower(r.getProperty().getTitle()).contains("agra") || lower(r.getProperty().getCity()).contains("agra"))
                .map(r -> {
                    Map<String, Object> offer = data("requestId", r.getId(), "propertyId", r.getProperty().getId(),
                            "property", r.getProperty().getTitle(), "status", r.getStatus(), "businessModel", r.getBusinessModel(),
                            "quantity", r.getQuantity(), "budget", r.getBudget());
                    proposalRepository.findByRequest_Id(r.getId()).ifPresent(p -> offer.put("terms", data(
                            "equipmentTotal", p.getEquipmentTotal(), "installationTotal", p.getInstallationTotal(),
                            "monthlyLease", p.getMonthlyLease(), "hostRevenueSharePercent", p.getHostRevenueSharePercent(),
                            "companyRevenueSharePercent", p.getCompanyRevenueSharePercent(), "validUntil", p.getValidUntil(),
                            "estimatedInstallationDays", p.getEstimatedInstallationDays(), "text", p.getTerms())));
                    return offer;
                }).toList();
        List<Map<String, Object>> coverageGaps = rows.stream().filter(r -> ((Number) r.get("availableCcs2")).longValue() == 0).toList();
        List<Map<String, Object>> acOnly = rows.stream().filter(r -> Boolean.TRUE.equals(r.get("acOnly"))).toList();
        return data("stationCount", rows.size(), "stations", rows.stream().limit(25).toList(),
                "companyOwnedStations", rows.stream().filter(r -> r.get("ownershipType") == StationOwnershipType.COMPANY_OWNED).count(),
                "hostPartneredStations", rows.stream().filter(r -> r.get("ownershipType") == StationOwnershipType.HOST_PARTNERED).count(),
                "healthyStations", rows.stream().filter(r -> ((Number) r.get("issueCount")).intValue() == 0).count(),
                "coverageGapCount", coverageGaps.size(), "coverageGaps", coverageGaps.stream().limit(25).toList(),
                "acOnlyCount", acOnly.size(), "acOnlyStations", acOnly.stream().limit(25).toList(),
                "maintenancePriorityCount", priorities.size(), "maintenancePriorities", priorities.stream().limit(25).toList(),
                "detailLimit", "Up to 25 stations per list; counts cover the entire selection. Ask for a city or exact station to narrow the view.",
                "rankingMethod", "Severity, affected journeys, station reservations, unavailable connectors, recorded downtime, occupancy",
                "properties", properties.stream().limit(25).toList(), "offers", offers.stream().limit(25).toList(),
                "dataLimits", List.of("Live occupancy and queue snapshots are stored; historical peak-demand forecasts are not inferred.",
                        "A missing incident start time means downtime is unknown. No repair-duration estimate is invented.",
                        "Booking values are recorded amounts, not lost-revenue forecasts. Net revenue requires recorded fees and payouts.",
                        "Only this Company's persisted proposals are visible. Other operators' private bids are not accessible.",
                        "Offer changes use the existing property workflow; Company cannot accept on behalf of a Host."));
    }

    private Optional<LandListing> resolveProperty(Long accountId, ChargingStation station) {
        if (station.getHostUserId() == null) return Optional.empty();
        Long requestId = station.getSourceInstallationRequestId();
        if (requestId == null && !station.isDemoData()) requestId = station.getHostPartnershipId();
        if (requestId != null) return requestRepository.findByIdAndCompany_Account_Id(requestId, accountId)
                .map(com.vidyut.marketplace.entity.InstallationRequest::getProperty)
                .filter(p -> Objects.equals(p.getHostUserId(), station.getHostUserId()));
        // Canonical seeder stores the property ID in hostPartnershipId; commercial stations store a request ID.
        if (station.isDemoData() && station.getHostPartnershipId() != null) return propertyRepository.findById(station.getHostPartnershipId())
                .filter(p -> Objects.equals(p.getHostUserId(), station.getHostUserId()));
        return Optional.empty();
    }

    public static boolean online(ChargingConnector c) {
        return c.getStatus() == ChargerStatus.ONLINE && c.isAvailable() && !c.isMaintenanceMode();
    }
    public static int severity(ChargingConnector c) {
        if (c.getStatus() == ChargerStatus.FAULT) return 4;
        if (c.getStatus() == ChargerStatus.SUSPECTED_FAULT) return 3;
        if (c.getStatus() == ChargerStatus.OFFLINE) return 2;
        if (c.getStatus() == ChargerStatus.MAINTENANCE || c.isMaintenanceMode() || c.getHealthScore() < 70) return 1;
        return 0;
    }
    private static boolean activeBooking(Booking b) {
        return (b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.IN_PROGRESS)
                && (b.getEndTime() != null ? b.getEndTime().isAfter(LocalDateTime.now())
                : b.getStartTime() == null || b.getStartTime().plusMinutes(Math.max(60, b.getDurationMinutes())).isAfter(LocalDateTime.now()));
    }
    private static boolean mentions(String q, ChargingStation s) {
        return (!lower(s.getCity()).isBlank() && q.matches("(?s).*\\b" + java.util.regex.Pattern.quote(lower(s.getCity())) + "\\b.*"))
                || q.contains(lower(s.getName())) || s.getConnectors().stream().anyMatch(c -> c.getChargerCode() != null && q.contains(lower(c.getChargerCode())));
    }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static Map<String, Object> data(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
