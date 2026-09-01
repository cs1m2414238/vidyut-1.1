package com.vidyut.station.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.dto.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChargingStationServiceImpl implements ChargingStationService {

    private final ChargingStationRepository stationRepository;

    @Value("${vidyut.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Override
    public StationResponse createStation(StationCreateRequest request, Long hostUserId) {
        ChargingStation station = buildStation(request);
        station.setHostUserId(hostUserId);
        station.setPropertyOwnerAccountId(hostUserId);
        station.setOwnershipType(StationOwnershipType.HOST_PARTNERED);
        addInitialConnector(station, request);
        return mapToResponse(stationRepository.save(station));
    }

    @Override
    public StationResponse createCompanyStation(StationCreateRequest request, Long companyAccountId,
                                                Long companyId, String companyName) {
        ChargingStation station = buildStation(request);
        station.setHostUserId(null);
        station.setPropertyOwnerAccountId(companyAccountId);
        station.setOperatorCompanyId(companyId);
        station.setOwnershipType(StationOwnershipType.COMPANY_OWNED);
        station.setPropertyOwnerName(valueOrDefault(request.getPropertyOwnerName(), companyName));
        station.setOperatorCompanyName(companyName);
        station.setEquipmentOwnerName(valueOrDefault(request.getEquipmentOwnerName(), companyName));
        station.setOperatingModel("COMPANY_OWNED_AND_OPERATED");
        addInitialConnector(station, request);
        return mapToResponse(stationRepository.save(station));
    }

    private ChargingStation buildStation(StationCreateRequest request) {
        return ChargingStation.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity() != null ? request.getCity() : "Lucknow")
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .pricePerKwh(request.getPricePerKwh())
                .rating(4.5)
                .reviewCount(10)
                .imageUrl(request.getImageUrl() != null ? request.getImageUrl() : "https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=300&q=80")
                .photoUrls(request.getPhotoUrls())
                .amenities(request.getAmenities())
                .workingHours(request.getWorkingHours() != null ? request.getWorkingHours() : "Open 24 hours")
                .weeklySchedule(request.getWeeklySchedule())
                .holidaySchedule(request.getHolidaySchedule())
                .chargingInstructions(request.getChargingInstructions())
                .propertyOwnerName(request.getPropertyOwnerName())
                .operatorCompanyName(request.getOperatorCompanyName())
                .equipmentOwnerName(request.getEquipmentOwnerName())
                .operatingModel(request.getOperatingModel())
                .solarProviderName(request.getSolarProviderName())
                .siteOwnershipDocumentUrl(request.getSiteOwnershipDocumentUrl())
                .electricityConnectionDocumentUrl(request.getElectricityConnectionDocumentUrl())
                .autoAvailability(request.isAutoAvailability())
                .bookingSlotMinutes(request.getBookingSlotMinutes() > 0 ? request.getBookingSlotMinutes() : 60)
                .status(StationStatus.ACTIVE)
                .availability(StationAvailability.AVAILABLE)
                .connectors(new ArrayList<>())
                .build();
    }

    private void addInitialConnector(ChargingStation station, StationCreateRequest request) {
        if (request.getConnectorType() != null) {
            ChargingConnector connector = ChargingConnector.builder()
                    .type(request.getConnectorType())
                    .powerKw(request.getPowerKw() > 0 ? request.getPowerKw() : 7.4)
                    .available(true)
                    .chargerCode("VY-" + System.currentTimeMillis())
                    .station(station)
                    .build();
            station.getConnectors().add(connector);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StationResponse getStationById(Long id) {
        ChargingStation station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));
        return mapToResponse(station);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResponse> getAllStations() {
        return publishedStations().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResponse> getStationsWithinBounds(double minLat, double maxLat, double minLng, double maxLng, int limit) {
        if (!Double.isFinite(minLat) || !Double.isFinite(maxLat)
                || !Double.isFinite(minLng) || !Double.isFinite(maxLng)
                || minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180
                || minLat > maxLat || minLng > maxLng) {
            throw new BadRequestException("Station bounds must be valid latitude/longitude ranges.");
        }
        int cappedLimit = Math.max(1, Math.min(limit > 0 ? limit : 250, 500));
        PageRequest page = PageRequest.of(0, cappedLimit,
                Sort.by("latitude").ascending().and(Sort.by("longitude")).and(Sort.by("id")));
        return stationRepository.findPublishedStationsWithinBounds(
                        minLat, maxLat, minLng, maxLng, demoDataEnabled, page)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyStationResponse> getNearbyStations(double latitude, double longitude, double radiusKm) {
        return publishedStations().stream()
                .map(s -> {
                    double dist = calculateDistance(latitude, longitude, s.getLatitude(), s.getLongitude());
                    return NearbyStationResponse.builder()
                            .station(mapToResponse(s))
                            .distanceKm(Math.round(dist * 10.0) / 10.0)
                            .build();
                })
                .filter(n -> n.getDistanceKm() <= radiusKm)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResponse> searchStations(String query, String connectorType, Double latitude, Double longitude,
                                                Double radiusKm, Integer minAvailableSlots, Double maxPricePerKwh,
                                                Double minPowerKw, Boolean availableOnly) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedConnector = connectorType == null ? ""
                : connectorType.replace("-", "").replace("_", "").replace(" ", "").toUpperCase(Locale.ROOT);
        double maxRadius = radiusKm == null || radiusKm <= 0 ? Double.MAX_VALUE : radiusKm;
        int requiredSlots = minAvailableSlots == null ? 0 : Math.max(0, minAvailableSlots);

        return publishedStations().stream()
                .map(station -> {
                    StationResponse response = mapToResponse(station);
                    if (latitude != null && longitude != null) {
                        response.setDistanceKm(round(calculateDistance(latitude, longitude,
                                station.getLatitude(), station.getLongitude())));
                    }
                    return response;
                })
                .filter(station -> normalizedQuery.isBlank()
                        || contains(station.getName(), normalizedQuery)
                        || contains(station.getAddress(), normalizedQuery)
                        || contains(station.getCity(), normalizedQuery))
                .filter(station -> normalizedConnector.isBlank() || station.getConnectors().stream()
                        .anyMatch(connector -> connector.getType().name().replace("_", "")
                                .equalsIgnoreCase(normalizedConnector)))
                .filter(station -> minPowerKw == null || station.getConnectors().stream()
                        .anyMatch(connector -> connector.getPowerKw() >= minPowerKw))
                .filter(station -> maxPricePerKwh == null || station.getPricePerKwh() <= maxPricePerKwh)
                .filter(station -> station.getDistanceKm() == null || station.getDistanceKm() <= maxRadius)
                .filter(station -> station.getAvailableSlots() >= requiredSlots)
                .filter(station -> !Boolean.TRUE.equals(availableOnly)
                        || (station.getStatus() == StationStatus.ACTIVE && station.getAvailableSlots() > 0))
                .sorted(Comparator.comparing((StationResponse station) -> station.getDistanceKm() == null
                                ? Double.MAX_VALUE : station.getDistanceKm())
                        .thenComparing(Comparator.comparingInt(StationResponse::getAvailableSlots).reversed())
                        .thenComparingDouble(StationResponse::getPricePerKwh))
                .toList();
    }

    private List<ChargingStation> publishedStations() {
        return stationRepository.findPublishedStations(demoDataEnabled);
    }

    @Override
    public StationResponse updateStation(Long id, StationUpdateRequest request) {
        ChargingStation station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));

        if (request.getName() != null) station.setName(request.getName());
        if (request.getAddress() != null) station.setAddress(request.getAddress());
        if (request.getPricePerKwh() != null) station.setPricePerKwh(request.getPricePerKwh());
        if (request.getStatus() != null) station.setStatus(request.getStatus());
        if (request.getAvailability() != null) station.setAvailability(request.getAvailability());
        applyExtendedUpdate(station, request);

        return mapToResponse(stationRepository.save(station));
    }

    @Override
    public StationResponse updateStation(Long id, Long ownerAccountId, StationUpdateRequest request) {
        ChargingStation station = stationRepository.findByIdAndHostUserId(id, ownerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found for this account"));
        if (request.getName() != null) station.setName(request.getName());
        if (request.getAddress() != null) station.setAddress(request.getAddress());
        if (request.getPricePerKwh() != null) station.setPricePerKwh(request.getPricePerKwh());
        if (request.getStatus() != null) station.setStatus(request.getStatus());
        if (request.getAvailability() != null) station.setAvailability(request.getAvailability());
        applyExtendedUpdate(station, request);
        return mapToResponse(stationRepository.save(station));
    }

    @Override
    public void deleteStation(Long id) {
        ChargingStation station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + id));
        if (station.isDemoData() || station.getDemoSeedKey() != null) {
            throw new BadRequestException("Core seeded demo charging stations cannot be permanently deleted.");
        }
        stationRepository.delete(station);
    }

    @Override
    public void deleteStation(Long id, Long ownerAccountId) {
        ChargingStation station = stationRepository.findByIdAndHostUserId(id, ownerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found for this account"));
        if (station.isDemoData() || station.getDemoSeedKey() != null) {
            throw new BadRequestException("Core seeded demo charging stations cannot be permanently deleted.");
        }
        stationRepository.delete(station);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResponse> getStationsByOwner(Long ownerAccountId) {
        return stationRepository.findByHostUserId(ownerAccountId).stream().map(this::mapToResponse).toList();
    }

    private StationResponse mapToResponse(ChargingStation station) {
        int totalSlots = station.getConnectors().size();
        int availableSlots = (int) station.getConnectors().stream()
                .filter(connector -> connector.isAvailable() && !connector.isMaintenanceMode()
                        && connector.getStatus() == ChargerStatus.ONLINE)
                .count();
        return StationResponse.builder()
                .id(station.getId())
                .name(station.getName())
                .address(station.getAddress())
                .city(station.getCity())
                .latitude(station.getLatitude())
                .longitude(station.getLongitude())
                .pricePerKwh(station.getPricePerKwh())
                .rating(station.getRating())
                .reviewCount(station.getReviewCount())
                .imageUrl(station.getImageUrl())
                .photoUrls(station.getPhotoUrls())
                .amenities(station.getAmenities())
                .workingHours(station.getWorkingHours())
                .weeklySchedule(station.getWeeklySchedule())
                .holidaySchedule(station.getHolidaySchedule())
                .chargingInstructions(station.getChargingInstructions())
                .autoAvailability(station.isAutoAvailability())
                .emergencyDisabled(station.isEmergencyDisabled())
                .demoData(station.isDemoData())
                .propertyOwnerName(station.getPropertyOwnerName())
                .operatorCompanyName(station.getOperatorCompanyName())
                .equipmentOwnerName(station.getEquipmentOwnerName())
                .operatingModel(station.getOperatingModel())
                .solarProviderName(station.getSolarProviderName())
                .bookingSlotMinutes(station.getBookingSlotMinutes())
                .queueCount(station.getQueueCount())
                .occupancyPercent(station.getOccupancyPercent())
                .dynamicPricingEnabled(station.isDynamicPricingEnabled())
                .timeBasedPricePerHour(station.getTimeBasedPricePerHour())
                .peakPricePerKwh(station.getPeakPricePerKwh())
                .peakHours(station.getPeakHours())
                .studentDiscountPercent(station.getStudentDiscountPercent())
                .corporatePricePerKwh(station.getCorporatePricePerKwh())
                .couponCode(station.getCouponCode())
                .couponDiscountPercent(station.getCouponDiscountPercent())
                .outletPartner(station.isOutletPartner())
                .outletInstitutionName(station.getOutletInstitutionName())
                .outletIdVerificationRequired(station.isOutletIdVerificationRequired())
                .status(station.getStatus())
                .availability(station.getAvailability())
                .hostUserId(station.getHostUserId())
                .propertyOwnerAccountId(station.getPropertyOwnerAccountId())
                .operatorCompanyId(station.getOperatorCompanyId())
                .hostPartnershipId(station.getHostPartnershipId())
                .ownershipType(station.getOwnershipType())
                .siteEvidenceComplete(present(station.getSiteOwnershipDocumentUrl())
                        && present(station.getElectricityConnectionDocumentUrl()))
                .connectors(new ArrayList<>(station.getConnectors()))
                .totalSlots(totalSlots)
                .availableSlots(availableSlots)
                .liveStatus(liveStatus(station, availableSlots))
                .build();
    }

    private String liveStatus(ChargingStation station, int availableSlots) {
        if (station.getStatus() != StationStatus.ACTIVE || station.isEmergencyDisabled()) return "OFFLINE";
        if (availableSlots > 0) return "AVAILABLE";
        if (station.getQueueCount() > 0) return "QUEUE";
        return "FULL";
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void applyExtendedUpdate(ChargingStation station, StationUpdateRequest request) {
        if (request.getCity() != null) station.setCity(request.getCity());
        if (request.getLatitude() != null) station.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) station.setLongitude(request.getLongitude());
        if (request.getImageUrl() != null) station.setImageUrl(request.getImageUrl());
        if (request.getPhotoUrls() != null) station.setPhotoUrls(request.getPhotoUrls());
        if (request.getAmenities() != null) station.setAmenities(request.getAmenities());
        if (request.getWorkingHours() != null) station.setWorkingHours(request.getWorkingHours());
        if (request.getWeeklySchedule() != null) station.setWeeklySchedule(request.getWeeklySchedule());
        if (request.getHolidaySchedule() != null) station.setHolidaySchedule(request.getHolidaySchedule());
        if (request.getChargingInstructions() != null) station.setChargingInstructions(request.getChargingInstructions());
        if (request.getPropertyOwnerName() != null) station.setPropertyOwnerName(request.getPropertyOwnerName());
        if (request.getOperatorCompanyName() != null) station.setOperatorCompanyName(request.getOperatorCompanyName());
        if (request.getEquipmentOwnerName() != null) station.setEquipmentOwnerName(request.getEquipmentOwnerName());
        if (request.getOperatingModel() != null) station.setOperatingModel(request.getOperatingModel());
        if (request.getSolarProviderName() != null) station.setSolarProviderName(request.getSolarProviderName());
        if (request.getSiteOwnershipDocumentUrl() != null) station.setSiteOwnershipDocumentUrl(request.getSiteOwnershipDocumentUrl());
        if (request.getElectricityConnectionDocumentUrl() != null) station.setElectricityConnectionDocumentUrl(request.getElectricityConnectionDocumentUrl());
        if (request.getAutoAvailability() != null) station.setAutoAvailability(request.getAutoAvailability());
        if (request.getEmergencyDisabled() != null) station.setEmergencyDisabled(request.getEmergencyDisabled());
        if (request.getBookingSlotMinutes() != null) station.setBookingSlotMinutes(Math.max(15, Math.min(480, request.getBookingSlotMinutes())));
        if (request.getQueueCount() != null) station.setQueueCount(Math.max(0, request.getQueueCount()));
        if (request.getOccupancyPercent() != null) station.setOccupancyPercent(Math.max(0, Math.min(100, request.getOccupancyPercent())));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double p = 0.017453292519943295;
        double a = 0.5 - Math.cos((lat2 - lat1) * p)/2 +
                Math.cos(lat1 * p) * Math.cos(lat2 * p) *
                        (1 - Math.cos((lon2 - lon1) * p))/2;
        return 12742 * Math.asin(Math.sqrt(a));
    }

    private String valueOrDefault(String value, String fallback) {
        return present(value) ? value.trim() : fallback;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
