package com.vidyut.company.service;

import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.admin.service.OperationalControlService;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.agent.service.RoleScopedAgentService;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.dto.*;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.CompanyActivityLog;
import com.vidyut.company.entity.CompanyEmployee;
import com.vidyut.company.entity.CompanyMaintenanceTicket;
import com.vidyut.company.entity.CompanyAgentMode;
import com.vidyut.company.entity.MaintenanceTicketStatus;
import com.vidyut.company.repository.CompanyEmployeeRepository;
import com.vidyut.company.repository.CompanyActivityLogRepository;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.payment.entity.Payment;
import com.vidyut.payment.entity.PaymentStatus;
import com.vidyut.payment.entity.Payout;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.payment.repository.PayoutRepository;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.dto.StationUpdateRequest;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.service.ChargingStationService;
import com.vidyut.session.entity.ChargingSession;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.session.repository.ChargingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyOperationsService {
    private final CompanyRepository companyRepository;
    private final CompanyOperatorContextService operatorContextService;
    private final CompanyEmployeeRepository employeeRepository;
    private final CompanyActivityLogRepository activityLogRepository;
    private final CompanyMaintenanceTicketRepository maintenanceTicketRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final ChargingStationService stationService;
    private final ChargingSessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final LandListingRepository landListingRepository;
    private final CompanyVerificationService verificationService;
    private final AutopilotService autopilotService;
    private final AdminControlService adminControlService;
    private final OperationalControlService operationalControlService;
    private final RoleScopedAgentService roleScopedAgentService;

    public List<StationResponse> getStations(Long accountId) {
        return getStations(accountId, null);
    }

    public List<StationResponse> getStations(Long accountId, String query) {
        Company company = requireCompany(accountId);
        return CompanyNetworkSearch.stations(managedStations(company, accountId), query).stream()
                .map(station -> stationService.getStationById(station.getId()))
                .toList();
    }

    @Transactional
    public StationResponse createStation(Long accountId, StationCreateRequest request) {
        operationalControlService.assertCompanyPublishingAllowed(accountId);
        Company company = requireVerifiedCompany(accountId);
        if (!present(request.getSiteOwnershipDocumentUrl())
                || !present(request.getElectricityConnectionDocumentUrl())) {
            throw new BadRequestException("Company-owned stations require site ownership/control and electricity connection evidence");
        }
        StationResponse station = stationService.createCompanyStation(request, accountId, company.getId(), company.getCompanyName());
        recordActivity(company, accountId, "CREATE", "STATION", station.getId(), "Created station " + station.getName());
        return station;
    }

    @Transactional
    public StationResponse updateStation(Long accountId, Long id, StationUpdateRequest request) {
        if (request.getStatus() == StationStatus.ACTIVE) {
            operationalControlService.assertCompanyPublishingAllowed(accountId);
        }
        Company company = requireVerifiedCompany(accountId);
        ownedStation(company, accountId, id);
        StationResponse station = stationService.updateStation(id, request);
        recordActivity(company, accountId, "UPDATE", "STATION", id, "Updated station " + station.getName());
        return station;
    }

    @Transactional
    public void deleteStation(Long accountId, Long id) {
        Company company = requireVerifiedCompany(accountId);
        ChargingStation station = ownedStation(company, accountId, id);
        if (station.getOwnershipType() == StationOwnershipType.HOST_PARTNERED) {
            throw new BadRequestException("A Host-partnered station must be closed through its partnership workflow");
        }
        if (station.isDemoData() || station.getDemoSeedKey() != null) {
            throw new BadRequestException("Core seeded demo charging stations cannot be permanently deleted.");
        }
        String stationName = station.getName();
        stationRepository.delete(station);
        recordActivity(company, accountId, "DELETE", "STATION", id, "Deleted station " + stationName);
    }

    public List<ChargerResponse> getChargers(Long accountId) {
        return getChargers(accountId, null);
    }

    public List<ChargerResponse> getChargers(Long accountId, String query) {
        Company company = requireCompany(accountId);
        return CompanyNetworkSearch.chargers(managedStations(company, accountId), query).stream()
                .map(this::mapCharger).toList();
    }

    @Transactional
    public ChargerResponse createCharger(Long accountId, ChargerRequest request) {
        operationalControlService.assertCompanyPublishingAllowed(accountId);
        Company company = requireVerifiedCompany(accountId);
        ChargingStation station = ownedStation(accountId, request.getStationId());
        if (connectorRepository.existsByChargerCodeIgnoreCase(request.getChargerCode())) {
            throw new DuplicateResourceException("Charger code already exists: " + request.getChargerCode());
        }
        ChargingConnector connector = applyCharger(ChargingConnector.builder()
                .station(station)
                .available(true)
                .build(), request);
        station.getConnectors().add(connector);
        ChargerResponse response = mapCharger(connectorRepository.save(connector));
        recordActivity(company, accountId, "CREATE", "CHARGER", response.getId(), "Provisioned charger " + response.getChargerCode());
        return response;
    }

    @Transactional
    public ChargerResponse updateCharger(Long accountId, Long id, ChargerRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingConnector connector = ownedCharger(accountId, id);
        ChargerStatus previous = connector.getStatus();
        boolean statusChange = request.getStatus() != previous || request.isMaintenanceMode() != connector.isMaintenanceMode();
        if (statusChange && !request.isImpactApproved()) throw new BadRequestException("Review and approve the connector status change before saving");
        if (statusChange && request.getExpectedStatus() != previous) throw new BadRequestException("The connector status changed. Refresh and review it again");
        if (request.isSyntheticDemo()) requireCanonicalDemo(connector);
        if (request.getStatus() == ChargerStatus.CHARGING && previous != ChargerStatus.CHARGING) throw new BadRequestException("CHARGING is controlled by an active charging session");
        if (!connector.getStation().getId().equals(request.getStationId())) throw new BadRequestException("A provisioned charger cannot be moved through its operational editor");
        if (statusChange && (previous == ChargerStatus.CHARGING || sessionRepository
                .findFirstByConnectorIdAndStatusOrderByStartedAtDesc(id, ChargingSessionStatus.ACTIVE).isPresent())) {
            throw new BadRequestException("End the active charging session before changing connector state");
        }
        if (connector.getStation().isDemoData() && !Objects.equals(connector.getChargerCode(), request.getChargerCode())) throw new BadRequestException("Keep the canonical demo connector code unchanged");
        boolean wasAvailable = connector.isAvailable();
        ChargingConnector saved = applyCharger(connector, request);
        if (!statusChange) saved.setAvailable(wasAvailable);
        if (statusChange) applyOperationalStatus(company, accountId, saved, request.getStatus(), request.isSyntheticDemo(), cleanReason(request.getFaultReason(), "Operator reported " + request.getStatus()));
        else connectorRepository.save(saved);
        ChargerResponse response = mapCharger(saved);
        recordActivity(company, accountId, "UPDATE", "CHARGER", id, "Updated charger " + response.getChargerCode());
        return response;
    }

    @Transactional
    public void deleteCharger(Long accountId, Long id) {
        Company company = requireVerifiedCompany(accountId);
        ChargingConnector charger = ownedCharger(accountId, id);
        if (canonicalDemo(charger)) throw new BadRequestException("Canonical demo connectors cannot be deleted");
        String chargerCode = charger.getChargerCode();
        connectorRepository.delete(charger);
        recordActivity(company, accountId, "DELETE", "CHARGER", id, "Deleted charger " + chargerCode);
    }

    public List<BookingResponse> getBookings(Long accountId) {
        requireCompany(accountId);
        return ownedBookings(accountId).stream().map(this::mapBooking).toList();
    }

    @Transactional
    public BookingResponse updateBookingStatus(Long accountId, Long bookingId, BookingStatus status) {
        Company company = requireVerifiedCompany(accountId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        ownedStation(accountId, booking.getStationId());
        booking.setStatus(status);
        BookingResponse response = mapBooking(bookingRepository.save(booking));
        recordActivity(company, accountId, "STATUS_CHANGE", "BOOKING", bookingId, "Changed booking status to " + status);
        return response;
    }

    @Transactional
    public StationResponse updatePricing(Long accountId, Long stationId, PricingRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingStation station = ownedStation(accountId, stationId);
        station.setPricePerKwh(request.getPricePerKwh());
        station.setTimeBasedPricePerHour(request.getTimeBasedPricePerHour());
        station.setPeakPricePerKwh(request.getPeakPricePerKwh());
        station.setPeakHours(request.getPeakHours());
        station.setStudentDiscountPercent(request.getStudentDiscountPercent());
        station.setCorporatePricePerKwh(request.getCorporatePricePerKwh());
        station.setDynamicPricingEnabled(request.isDynamicPricingEnabled());
        station.setCouponCode(request.getCouponCode());
        station.setCouponDiscountPercent(request.getCouponDiscountPercent());
        stationRepository.save(station);
        StationResponse response = stationService.getStationById(stationId);
        recordActivity(company, accountId, "UPDATE", "PRICING", stationId, "Updated pricing for " + response.getName());
        return response;
    }

    public Map<String, Object> dashboard(Long accountId) {
        Company company = requireCompany(accountId);
        List<ChargingStation> stations = managedStations(company, accountId);
        List<ChargingConnector> chargers = stations.stream().flatMap(station -> station.getConnectors().stream()).toList();
        List<Booking> bookings = bookingsForStations(stations);
        List<Payment> payments = paymentsForBookings(bookings);
        double revenue = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount).sum();
        if (revenue == 0) revenue = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .mapToDouble(Booking::getTotalAmount).sum();
        long online = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE).count();
        long busy = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.CHARGING).count();
        long faults = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.FAULT || charger.getStatus() == ChargerStatus.OFFLINE).count();
        double utilization = chargers.isEmpty() ? 0 : Math.round((busy * 1000.0 / chargers.size())) / 10.0;
        double energy = bookings.stream().mapToDouble(Booking::getKwhDelivered).sum();
        int queue = stations.stream().mapToInt(ChargingStation::getQueueCount).sum();
        return linkedMap(
                "totalStations", stations.size(),
                "totalChargers", chargers.size(),
                "onlineChargers", online,
                "busyChargers", busy,
                "faults", faults,
                "utilizationRate", utilization,
                "activeSessions", bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.IN_PROGRESS).count(),
                "queueCount", queue,
                "energyDeliveredKwh", round(energy),
                "revenue", round(revenue),
                "occupancyPercent", round(stations.stream().mapToDouble(ChargingStation::getOccupancyPercent).average().orElse(0)),
                "alerts", alerts(chargers)
        );
    }

    public Map<String, Object> analytics(Long accountId) {
        Map<String, Object> dashboard = dashboard(accountId);
        Company company = requireCompany(accountId);
        List<ChargingStation> stations = managedStations(company, accountId);
        List<Booking> bookings = bookingsForStations(stations);
        Map<String, Double> stationRevenue = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .collect(Collectors.groupingBy(Booking::getStationName, Collectors.summingDouble(Booking::getTotalAmount)));
        List<Map<String, Object>> topStations = stationRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(5)
                .map(entry -> linkedMap("station", entry.getKey(), "revenue", round(entry.getValue()))).toList();
        Map<Integer, Long> hourCounts = bookings.stream().filter(booking -> booking.getStartTime() != null)
                .collect(Collectors.groupingBy(booking -> booking.getStartTime().getHour(), Collectors.counting()));
        int peakHour = hourCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(18);
        return linkedMap(
                "summary", dashboard,
                "dailyRevenue", revenueSince(bookings, LocalDate.now()),
                "weeklyRevenue", revenueSince(bookings, LocalDate.now().minusDays(6)),
                "monthlyRevenue", revenueSince(bookings, LocalDate.now().withDayOfMonth(1)),
                "peakUsageHour", String.format("%02d:00", peakHour),
                "topStations", topStations,
                "customerGrowthPercent", growth(bookings),
                "successfulSessions", bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED).count()
        );
    }

    public CompanyNetworkResponse network(Long accountId) {
        Company company = requireCompany(accountId);
        List<ChargingStation> stations = managedStations(company, accountId);
        List<ChargingConnector> chargers = stations.stream()
                .flatMap(station -> station.getConnectors().stream())
                .toList();
        List<ManagedStationResponse> stationResponses = stations.stream().map(station -> {
            List<ChargingConnector> stationChargers = station.getConnectors();
            return new ManagedStationResponse(station.getId(), station.getName(), station.getCity(), station.getAddress(),
                    station.getHostUserId(), station.getPropertyOwnerName(), station.getOperatorCompanyName(),
                    station.getOwnershipType(), station.getHostPartnershipId(), relationship(station, accountId),
                    station.getStatus(), stationChargers.size(),
                    (int) stationChargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE).count(),
                    (int) stationChargers.stream().filter(this::needsAttention).count());
        }).toList();
        List<ManagedChargerResponse> chargerResponses = chargers.stream()
                .sorted(Comparator.comparingInt(ChargingConnector::getHealthScore))
                .map(charger -> managedChargerResponse(charger, relationship(charger.getStation(), accountId)))
                .toList();
        int openTickets = (int) maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(company.getId()).stream()
                .filter(ticket -> ticket.getStatus() == MaintenanceTicketStatus.OPEN
                        || ticket.getStatus() == MaintenanceTicketStatus.IN_PROGRESS)
                .count();
        return new CompanyNetworkResponse(stations.size(), chargers.size(),
                (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE).count(),
                (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.CHARGING).count(),
                (int) chargers.stream().filter(this::needsAttention).count(), openTickets,
                stationResponses, chargerResponses);
    }

    public List<MaintenanceTicketResponse> maintenanceTickets(Long accountId) {
        Company company = requireCompany(accountId);
        return maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(company.getId()).stream()
                .map(this::maintenanceTicketResponse)
                .toList();
    }

    @Transactional
    public MaintenanceTicketResponse createMaintenanceTicket(Long accountId, MaintenanceTicketCreateRequest request) {
        Company company = requireVerifiedCompany(accountId);
        ChargingConnector charger = managedCharger(company, accountId, request.chargerId());
        if (maintenanceTicketRepository.existsByCompanyIdAndChargerIdAndStatusIn(company.getId(), charger.getId(),
                List.of(MaintenanceTicketStatus.OPEN, MaintenanceTicketStatus.IN_PROGRESS))) {
            throw new DuplicateResourceException("An active maintenance ticket already exists for " + charger.getChargerCode());
        }
        CompanyMaintenanceTicket ticket = maintenanceTicketRepository.save(CompanyMaintenanceTicket.builder()
                .companyId(company.getId())
                .chargerId(charger.getId())
                .chargerCode(charger.getChargerCode())
                .stationId(charger.getStation().getId())
                .stationName(charger.getStation().getName())
                .city(charger.getStation().getCity())
                .priority(request.priority())
                .status(MaintenanceTicketStatus.OPEN)
                .issue(request.issue().trim())
                .assignedTo(clean(request.assignedTo()))
                .build());
        recordActivity(company, accountId, "CREATE", "MAINTENANCE_TICKET", ticket.getId(),
                "Opened " + ticket.getPriority() + " maintenance ticket for " + charger.getChargerCode());
        return maintenanceTicketResponse(ticket);
    }

    @Transactional
    public MaintenanceTicketResponse updateMaintenanceTicket(Long accountId, Long id,
                                                               MaintenanceTicketUpdateRequest request) {
        Company company = requireVerifiedCompany(accountId);
        CompanyMaintenanceTicket ticket = maintenanceTicketRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance ticket not found for this company"));
        ticket.setStatus(request.status());
        if (request.priority() != null) ticket.setPriority(request.priority());
        ticket.setAssignedTo(clean(request.assignedTo()));
        ticket.setResolutionNote(clean(request.resolutionNote()));
        ticket.setUpdatedAt(LocalDateTime.now());
        if (request.status() == MaintenanceTicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
            if (request.restoreChargerOnline()) {
                ChargingConnector charger = managedCharger(company, accountId, ticket.getChargerId());
                charger.setStatus(ChargerStatus.ONLINE);
                charger.setMaintenanceMode(false);
                charger.setAvailable(true);
                charger.setFaultCode(null);
                charger.setHealthScore(Math.max(80, charger.getHealthScore()));
                charger.setLastHeartbeat(LocalDateTime.now());
                connectorRepository.save(charger);
                ChargingStation station = charger.getStation();
                if (station.getStatus() == StationStatus.ACTIVE && !station.isEmergencyDisabled()) {
                    station.setAvailability(StationAvailability.AVAILABLE);
                    stationRepository.save(station);
                }
            }
        } else {
            ticket.setResolvedAt(null);
        }
        CompanyMaintenanceTicket saved = maintenanceTicketRepository.save(ticket);
        recordActivity(company, accountId, "STATUS_CHANGE", "MAINTENANCE_TICKET", saved.getId(),
                "Moved maintenance ticket for " + saved.getChargerCode() + " to " + saved.getStatus());
        return maintenanceTicketResponse(saved);
    }

    public CompanySettlementResponse settlements(Long accountId) {
        requireCompany(accountId);
        List<Booking> bookings = ownedBookings(accountId);
        Map<Long, Booking> bookingById = bookings.stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity(), (left, right) -> left));
        List<Payment> payments = paymentsForBookings(bookings);
        double collected = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount).sum();
        double pending = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
                .mapToDouble(Payment::getAmount).sum();
        double refunded = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.REFUNDED)
                .mapToDouble(Payment::getAmount).sum();
        List<CompanySettlementTransactionResponse> transactions = payments.stream()
                .sorted(Comparator.comparing(Payment::getTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .map(payment -> {
                    Booking booking = bookingById.get(payment.getBookingId());
                    return new CompanySettlementTransactionResponse(payment.getId(), payment.getBookingId(),
                            booking == null ? "Station payment" : booking.getStationName(), payment.getAmount(),
                            payment.getStatus(), payment.getGatewayTransactionId(), payment.getTimestamp());
                }).toList();
        return new CompanySettlementResponse(round(collected), round(pending), round(refunded),
                round(collected - refunded),
                payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).count(), transactions);
    }

    public List<CompanyEmployee> getEmployees(Long accountId) {
        return employeeRepository.findByCompanyIdOrderByCreatedAtDesc(requireCompany(accountId).getId());
    }

    public List<CompanyActivityLog> getActivityLogs(Long accountId) {
        return activityLogRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(requireCompany(accountId).getId());
    }

    @Transactional
    public CompanyEmployee createEmployee(Long accountId, EmployeeRequest request) {
        Company company = requireVerifiedCompany(accountId);
        if (employeeRepository.existsByCompanyIdAndEmailIgnoreCase(company.getId(), request.getEmail())) {
            throw new DuplicateResourceException("Employee already exists: " + request.getEmail());
        }
        CompanyEmployee employee = employeeRepository.save(applyEmployee(CompanyEmployee.builder().companyId(company.getId()).build(), request));
        recordActivity(company, accountId, "CREATE", "EMPLOYEE", employee.getId(), "Added employee " + employee.getName() + " as " + employee.getRole());
        return employee;
    }

    @Transactional
    public CompanyEmployee updateEmployee(Long accountId, Long id, EmployeeRequest request) {
        Company company = requireVerifiedCompany(accountId);
        CompanyEmployee employee = employeeRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for this company"));
        CompanyEmployee saved = employeeRepository.save(applyEmployee(employee, request));
        recordActivity(company, accountId, "UPDATE", "EMPLOYEE", id, "Updated employee " + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteEmployee(Long accountId, Long id) {
        Company company = requireVerifiedCompany(accountId);
        CompanyEmployee employee = employeeRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for this company"));
        employeeRepository.delete(employee);
        recordActivity(company, accountId, "DELETE", "EMPLOYEE", id, "Removed employee " + employee.getName());
    }

    public CompanyAgentSettingsResponse agentSettings(Long accountId) {
        return agentSettingsResponse(requireCompany(accountId));
    }

    @Transactional
    public CompanyAgentSettingsResponse updateAgentSettings(Long accountId, CompanyAgentSettingsRequest request) {
        Company company = requireVerifiedCompany(accountId);
        company.setAgentMode(request.mode());
        company.setAgentMaxPriceChangePercent(request.maxPriceChangePercent());
        company.setAgentAutoDisableFaultyChargers(request.autoDisableFaultyChargers());
        company.setAgentAutoCreateMaintenanceTickets(request.autoCreateMaintenanceTickets());
        Company saved = companyRepository.save(company);
        recordActivity(saved, accountId, "UPDATE", "COMPANY_AGENT", saved.getId(),
                "Updated Company Assistant autonomy to " + saved.getAgentMode());
        return agentSettingsResponse(saved);
    }

    public CompanyAgentResponse askAssistant(Long accountId, String rawQuestion) {
        return askAssistant(accountId, rawQuestion, null);
    }

    public CompanyAgentResponse askAssistant(Long accountId, String rawQuestion, String authorization) {
        Company company = requireCompany(accountId);
        List<ChargingStation> stations = managedStations(company, accountId);
        List<ChargingConnector> chargers = stations.stream().flatMap(station -> station.getConnectors().stream()).toList();
        List<Booking> bookings = bookingsForStations(stations);
        String question = rawQuestion.toLowerCase(Locale.ROOT).replaceAll("\\bagar\\b", "agra");
        String intent = assistantIntent(question);
        CompanyAgentResponse.NetworkSummary network = agentNetworkSummary(stations, chargers, bookings);
        CompanyAgentResponse.RevenueSummary revenue = agentRevenueSummary(stations, bookings);
        CompanyAgentResponse.PricingRecommendation pricing = agentPricingRecommendation(company, stations);
        List<CompanyAgentResponse.SiteRecommendation> sites = expansionSites(question);
        Map<String, Object> operations = operatorContextService.inspect(accountId, question, stations, bookings);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> priorities = (List<Map<String, Object>>) operations.getOrDefault("maintenancePriorities", List.of());
        Long priorityStationId = priorities.isEmpty() ? null : ((Number) priorities.get(0).get("stationId")).longValue();
        List<ChargingConnector> priorityChargers = priorityStationId == null ? List.of() : chargers.stream()
                .filter(c -> priorityStationId.equals(c.getStation().getId())).toList();
        CompanyAgentResponse.FaultImpact fault = agentFaultImpact(stations, priorityChargers, bookings);
        List<CompanyAgentResponse.RecommendedAction> actions = "DEMO_OPERATION".equals(intent)
                ? demoActions(question, chargers) : agentActions(intent, company, fault, pricing);
        Map<String, Object> offerDraft = "OFFER".equals(intent) && (question.contains("prepare") || question.contains("draft")) ? agentOfferDraft(rawQuestion, sites) : Map.of();
        String answer = operatorAnswer(intent, question, assistantAnswer(intent, network, fault, revenue, pricing, sites, offerDraft), operations, actions);
        Map<String, Object> context = linkedMap("companyName", company.getCompanyName(), "mode", company.getAgentMode(),
                "intent", intent, "network", network, "operations", operations, "fault", fault, "revenue", revenue,
                "pricing", pricing, "siteRecommendations", sites, "proposedActions", actions, "offerDraft", offerDraft,
                "ownershipBreakdown", linkedMap("companyOwnedStations", stations.stream().filter(st -> st.getOwnershipType() == StationOwnershipType.COMPANY_OWNED).count(),
                        "hostPartneredStations", stations.stream().filter(st -> st.getOwnershipType() == StationOwnershipType.HOST_PARTNERED).count()),
                "approvalPolicy", "Every write requires explicit approval, even in AUTOPILOT mode. Company operates hardware; Host reports issues; driver approves reroutes.");
        RoleScopedAgentService.GroundedReply grounded = authorization != null && !authorization.isBlank() && roleScopedAgentService != null
                ? roleScopedAgentService.explain(authorization, "COMPANY", accountId, rawQuestion, answer, context)
                : new RoleScopedAgentService.GroundedReply(answer, "deterministic-company-fallback", "DETERMINISTIC", true);
        return new CompanyAgentResponse(intent, company.getAgentMode(), grounded.answer(), network, fault, revenue,
                pricing, sites, actions, offerDraft, operations, grounded.model(), grounded.provider(), grounded.deterministicFallback(), LocalDateTime.now());
    }

    @Transactional
    public CompanyAgentActionResponse executeAgentAction(Long accountId, CompanyAgentActionRequest request) {
        Company company = requireVerifiedCompany(accountId);
        if (company.getAgentMode() == CompanyAgentMode.RECOMMEND_ONLY) {
            return new CompanyAgentActionResponse("RECOMMENDED_ONLY",
                    "Recommend-only mode never changes the network. Switch mode or perform the action manually.",
                    request.action(), Map.of(), null);
        }
        if (!request.approved()) {
            return new CompanyAgentActionResponse("AWAITING_APPROVAL",
                    "This action is prepared but needs company approval.", request.action(), Map.of(), null);
        }

        Map<String, Object> result;
        String message;
        switch (request.action()) {
            case DISABLE_NEW_BOOKINGS -> {
                ChargingConnector charger = managedCharger(company, accountId, required(request.chargerId(), "Choose a charger"));
                charger.setAvailable(false);
                charger.setLastHeartbeat(LocalDateTime.now());
                connectorRepository.save(charger);
                ChargingStation station = charger.getStation();
                boolean hasBackup = station.getConnectors().stream().anyMatch(candidate -> !candidate.getId().equals(charger.getId())
                        && candidate.isAvailable() && candidate.getStatus() == ChargerStatus.ONLINE && !candidate.isMaintenanceMode());
                if (!hasBackup) {
                    station.setAvailability(StationAvailability.UNAVAILABLE);
                    stationRepository.save(station);
                }
                Map<String, Object> reroute = autopilotService.handleConnectorUnavailable(station.getId(),
                        charger.getType().name(), charger.getId(), cleanReason(request.reason(), "Company Assistant isolated a charger"));
                result = linkedMap("chargerId", charger.getId(), "chargerCode", charger.getChargerCode(),
                        "stationId", station.getId(), "stationStillAvailable", hasBackup, "journeyReroute", reroute);
                message = "New assignments to " + charger.getChargerCode() + " are disabled; other healthy chargers remain available.";
                recordActivity(company, accountId, "AGENT_ACTION", "CHARGER", charger.getId(), message);
            }
            case SIMULATE_DEMO_FAULT, RESTORE_DEMO_CHARGER, PUT_DEMO_CHARGER_IN_MAINTENANCE -> {
                ChargingConnector charger = managedCharger(company, accountId, required(request.chargerId(), "Choose an exact demo connector"));
                requireCanonicalDemo(charger);
                ChargerStatus target = request.action() == CompanyAgentActionType.SIMULATE_DEMO_FAULT ? ChargerStatus.FAULT
                        : request.action() == CompanyAgentActionType.RESTORE_DEMO_CHARGER ? ChargerStatus.ONLINE : ChargerStatus.MAINTENANCE;
                if (charger.getStatus() != request.expectedStatus()) throw new BadRequestException("Connector status changed. Ask again and review the new action");
                if (charger.getStatus() == ChargerStatus.CHARGING || sessionRepository.findFirstByConnectorIdAndStatusOrderByStartedAtDesc(charger.getId(), ChargingSessionStatus.ACTIVE).isPresent()) throw new BadRequestException("End the active charging session before changing connector state");
                if (target == ChargerStatus.FAULT && charger.getStatus() != ChargerStatus.ONLINE) throw new BadRequestException("Restore the demo connector before simulating a new fault");
                if (target == ChargerStatus.ONLINE && charger.getStatus() == ChargerStatus.ONLINE) throw new BadRequestException("The demo connector is already online");
                charger.setMaintenanceMode(target == ChargerStatus.MAINTENANCE);
                result = applyOperationalStatus(company, accountId, charger, target, true, cleanReason(request.reason(), "Simulated charger communication failure"));
                message = charger.getChargerCode() + " is now " + target + ". Synthetic demo event recorded.";
            }
            case CREATE_MAINTENANCE_TICKET -> {
                Long chargerId = required(request.chargerId(), "Choose a charger");
                managedCharger(company, accountId, chargerId);
                CompanyMaintenanceTicket existing = maintenanceTicketRepository.findFirstByCompanyIdAndChargerIdAndStatusInOrderByUpdatedAtDesc(company.getId(), chargerId,
                        List.of(MaintenanceTicketStatus.OPEN, MaintenanceTicketStatus.IN_PROGRESS)).orElse(null);
                MaintenanceTicketResponse ticket = existing == null ? createMaintenanceTicket(accountId, new MaintenanceTicketCreateRequest(chargerId,
                        request.priority() == null ? com.vidyut.company.entity.MaintenancePriority.HIGH : request.priority(), cleanReason(request.reason(), "Operator review requested"), null)) : maintenanceTicketResponse(existing);
                result = linkedMap("ticketId", ticket.id(), "chargerId", chargerId, "status", ticket.status());
                message = "Maintenance work order #" + ticket.id() + " is " + ticket.status() + ". Assign a technician in Maintenance. Connector state is unchanged.";
            }
            case NOTIFY_STATION_MANAGER -> {
                ChargingConnector charger = managedCharger(company, accountId, required(request.chargerId(), "Choose a charger"));
                message = "Station manager notification recorded for " + charger.getChargerCode() + ": "
                        + cleanReason(request.reason(), "Review the charger condition and acknowledge the incident");
                recordActivity(company, accountId, "AGENT_ACTION", "STATION_NOTIFICATION", charger.getStation().getId(), message);
                result = linkedMap("stationId", charger.getStation().getId(), "chargerId", charger.getId(), "notificationRecorded", true);
            }
            case APPLY_PRICE_RECOMMENDATION -> {
                ChargingStation station = ownedStation(accountId, required(request.stationId(), "Choose a station"));
                double proposed = request.proposedPricePerKwh() == null ? station.getPricePerKwh() : request.proposedPricePerKwh();
                if (!Double.isFinite(proposed) || proposed <= 0) throw new BadRequestException("Price must be positive and finite");
                double percent = station.getPricePerKwh() <= 0 ? 0
                        : Math.abs(proposed - station.getPricePerKwh()) / station.getPricePerKwh() * 100;
                if (percent > company.getAgentMaxPriceChangePercent() + 0.001) {
                    throw new BadRequestException("Price change exceeds the Company Assistant limit of "
                            + company.getAgentMaxPriceChangePercent() + "%");
                }
                double previous = station.getPricePerKwh();
                station.setPricePerKwh(round(proposed));
                stationRepository.save(station);
                result = linkedMap("stationId", station.getId(), "previousPricePerKwh", previous,
                        "pricePerKwh", station.getPricePerKwh(), "changePercent", round(percent));
                message = "Approved station price applied to " + station.getName() + ".";
                recordActivity(company, accountId, "AGENT_ACTION", "PRICING", station.getId(), message);
            }
            default -> throw new BadRequestException("Unsupported Company Assistant action");
        }
        return new CompanyAgentActionResponse("EXECUTED", message, request.action(), result, LocalDateTime.now());
    }

    private List<CompanyAgentResponse.SiteRecommendation> expansionSites(String question) {
        List<ChargingStation> activeStations = stationRepository.findAll().stream()
                .filter(station -> station.getStatus() == StationStatus.ACTIVE && (station.getLatitude() != 0 || station.getLongitude() != 0) && station.getConnectors().stream().anyMatch(CompanyOperatorContextService::online)).toList();
        java.util.regex.Matcher powerMatch = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*kw").matcher(question);
        double requestedPower = powerMatch.find() ? Double.parseDouble(powerMatch.group(1)) : 0;
        return landListingRepository.findByDiscoverableTrueAndStatusIn(List.of(LandListingStatus.APPROVED, LandListingStatus.ACTIVE))
                .stream().filter(site -> site.getAvailableParkingBays() > 0 && site.getAvailableLoadKw() >= Math.max(7, requestedPower))
                .filter(site -> site.getLatitude() != 0 || site.getLongitude() != 0)
                .filter(site -> !question.contains("ccs2") || site.getPreferredConnectorType() == null || site.getPreferredConnectorType().isBlank() || site.getPreferredConnectorType().equalsIgnoreCase("CCS2"))
                .map(site -> {
                    Double nearestKm = activeStations.stream()
                            .mapToDouble(station -> distanceKm(site.getLatitude(), site.getLongitude(), station.getLatitude(), station.getLongitude()))
                            .min().stream().boxed().findFirst().orElse(null);
                    double score = Math.min(100, site.getAvailableParkingBays() * 6.0
                            + Math.min(45, site.getAvailableLoadKw() * 0.7) + Math.min(35, (nearestKm == null ? 0 : nearestKm) * 0.7));
                    String location = java.util.stream.Stream.of(site.getCity(), site.getState()).filter(Objects::nonNull)
                            .filter(value -> !value.isBlank()).collect(Collectors.joining(", "));
                    double power = requestedPower > 0 ? requestedPower : site.getAvailableLoadKw() >= 120 ? 120 : site.getAvailableLoadKw() >= 60 ? 60 : Math.min(30, site.getAvailableLoadKw());
                    int count = Math.max(1, Math.min(site.getAvailableParkingBays(), Math.min(4, (int) Math.floor(site.getAvailableLoadKw() / power))));
                    return new CompanyAgentResponse.SiteRecommendation(site.getId(), site.getTitle(),
                            location.isBlank() ? site.getAddress() : location, site.getAvailableParkingBays(),
                            site.getAvailableLoadKw(), nearestKm == null ? null : round(nearestKm), round(score), count, power,
                            clean(site.getPreferredConnectorType()) == null ? "CCS2" : site.getPreferredConnectorType(),
                            site.getAvailableLoadKw() >= 60
                                    ? "Stored bays and load support this preliminary setup; confirm free grid headroom and partnership terms in a survey"
                                    : "Underserved Host location; confirm transformer capacity during the survey");
                }).sorted(Comparator.comparingDouble(CompanyAgentResponse.SiteRecommendation::expansionScore).reversed())
                .limit(5).toList();
    }

    private String assistantIntent(String question) {
        if ((question.contains("simulate") || question.contains("mark") || question.contains("restore") || question.contains("put "))
                && (question.contains("fault") || question.contains("maintenance") || question.contains("restore"))
                && (question.contains("demo") || question.contains("connector") || question.contains("charger"))) return "DEMO_OPERATION";
        if (question.contains("offer") || question.contains("proposal") || question.contains("investment")) return "OFFER";
        if (question.contains("next ") || question.contains("deploy") || question.contains("expand") || question.contains("install") || question.contains("property") || question.contains("properties")) return "EXPANSION";
        if (question.contains("maintenance") || question.contains("fault") || question.contains("failure") || question.contains("offline") || question.contains("repair") || question.contains("attention") || question.contains("issues") || question.contains("impact") || question.contains("downtime")) return "FAULT";
        if (question.contains("ccs2") || question.contains("coverage") || question.contains("only ac") || question.contains("ac charging")) return "CONNECTOR_AVAILABILITY";
        if (question.contains("company-owned") || question.contains("company owned") || question.contains("host-partnered") || question.contains("host partnered") || question.contains("ownership")) return "OWNERSHIP";
        if (question.contains("revenue") || question.contains("earn") || question.contains("settlement")) return "REVENUE";
        if (question.contains("price") || question.contains("pricing") || question.contains("tariff")) return "PRICING";
        return "NETWORK";
    }

    private CompanyAgentResponse.NetworkSummary agentNetworkSummary(List<ChargingStation> stations,
            List<ChargingConnector> chargers, List<Booking> bookings) {
        List<Long> stationIds = stations.stream().map(ChargingStation::getId).toList();
        List<ChargingSession> sessions = stationIds.isEmpty() ? List.of()
                : sessionRepository.findByStationIdInAndStatusOrderByStartedAtDesc(stationIds, ChargingSessionStatus.ACTIVE);
        int occupied = (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.CHARGING).count();
        int available = (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE
                && charger.isAvailable() && !charger.isMaintenanceMode()).count();
        int offline = (int) chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.OFFLINE
                || charger.getStatus() == ChargerStatus.MAINTENANCE || charger.isMaintenanceMode()).count();
        int faults = (int) chargers.stream().filter(c -> c.getStatus() == ChargerStatus.FAULT || c.getStatus() == ChargerStatus.SUSPECTED_FAULT).count();
        LocalDateTime now = LocalDateTime.now();
        int reserved = (int) bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED
                && booking.getStartTime() != null && !booking.getStartTime().isBefore(now)
                && booking.getStartTime().isBefore(now.plusMinutes(30))).count();
        ChargingStation highest = stations.stream().max(Comparator.comparingDouble(ChargingStation::getOccupancyPercent)).orElse(null);
        ChargingSession longest = sessions.stream().filter(session -> session.getStartedAt() != null)
                .min(Comparator.comparing(ChargingSession::getStartedAt)).orElse(null);
        ChargingSession next = sessions.stream().filter(session -> session.getEstimatedCompletionAt() != null)
                .min(Comparator.comparing(ChargingSession::getEstimatedCompletionAt)).orElse(null);
        Map<Long, String> chargerCodes = chargers.stream().filter(charger -> charger.getId() != null)
                .collect(Collectors.toMap(ChargingConnector::getId,
                        charger -> charger.getChargerCode() == null ? "Charger #" + charger.getId() : charger.getChargerCode(),
                        (left, right) -> left));
        String immediate = chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.ONLINE
                        && charger.isAvailable() && !charger.isMaintenanceMode())
                .map(charger -> charger.getChargerCode() == null ? "Charger #" + charger.getId() : charger.getChargerCode())
                .findFirst().orElse("No charger predicted");
        return new CompanyAgentResponse.NetworkSummary(stations.size(), chargers.size(), occupied, available,
                reserved, offline, faults, sessions.size(), highest == null ? "No station data" : highest.getName(),
                highest == null ? 0 : round(highest.getOccupancyPercent()),
                longest == null ? "No active session" : chargerCodes.getOrDefault(longest.getConnectorId(), "Session #" + longest.getId()),
                longest == null ? 0 : Math.max(0, Duration.between(longest.getStartedAt(), now).toMinutes()),
                next == null ? immediate : chargerCodes.getOrDefault(next.getConnectorId(), "Session #" + next.getId()),
                next == null ? 0 : Math.max(0, Duration.between(now, next.getEstimatedCompletionAt()).toMinutes()));
    }

    private CompanyAgentResponse.FaultImpact agentFaultImpact(List<ChargingStation> stations,
            List<ChargingConnector> chargers, List<Booking> bookings) {
        ChargingConnector fault = chargers.stream().filter(this::needsAttention)
                .sorted(Comparator.comparing((ChargingConnector charger) -> charger.getStatus() != ChargerStatus.FAULT)
                        .thenComparingInt(ChargingConnector::getHealthScore))
                .findFirst().orElse(null);
        if (fault == null) return null;
        LocalDateTime now = LocalDateTime.now();
        List<Booking> affected = bookings.stream().filter(booking -> booking.getStationId().equals(fault.getStation().getId()))
                .filter(booking -> booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED)
                .filter(booking -> booking.getStartTime() == null || (!booking.getStartTime().isBefore(now)
                        && booking.getStartTime().isBefore(now.plusHours(3))))
                .toList();
        Integer downtime = null;
        double revenueRisk = affected.stream().mapToDouble(Booking::getTotalAmount).sum();
        List<String> backups = fault.getStation().getConnectors().stream()
                .filter(charger -> !charger.getId().equals(fault.getId()) && charger.getType() == fault.getType())
                .filter(charger -> charger.getStatus() == ChargerStatus.ONLINE && charger.isAvailable() && !charger.isMaintenanceMode())
                .map(charger -> charger.getChargerCode() == null ? "Charger #" + charger.getId() : charger.getChargerCode())
                .limit(4).toList();
        if (backups.isEmpty()) {
            backups = stations.stream().filter(station -> !station.getId().equals(fault.getStation().getId()))
                    .flatMap(station -> station.getConnectors().stream())
                    .filter(charger -> charger.getType() == fault.getType() && charger.getStatus() == ChargerStatus.ONLINE
                            && charger.isAvailable() && !charger.isMaintenanceMode())
                    .sorted(Comparator.comparingDouble(charger -> distanceKm(fault.getStation().getLatitude(),
                            fault.getStation().getLongitude(), charger.getStation().getLatitude(), charger.getStation().getLongitude())))
                    .map(charger -> charger.getStation().getName() + " · " + charger.getChargerCode()).limit(4).toList();
        }
        String issue = clean(fault.getFaultCode());
        if (issue == null) issue = fault.getStatus() == ChargerStatus.FAULT ? "Hardware fault reported"
                : fault.isMaintenanceMode() ? "Maintenance mode is active" : "Heartbeat or health requires attention";
        return new CompanyAgentResponse.FaultImpact(fault.getId(), fault.getChargerCode(), fault.getStation().getName(),
                issue, affected.size(), downtime, round(revenueRisk), backups);
    }

    private CompanyAgentResponse.RevenueSummary agentRevenueSummary(List<ChargingStation> stations, List<Booking> bookings) {
        LocalDate today = LocalDate.now();
        List<Booking> completed = bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .filter(booking -> booking.getStartTime() != null && booking.getStartTime().toLocalDate().equals(today)).toList();
        List<Payment> payments = paymentsForBookings(bookings);
        double chargingRevenue = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
                .filter(payment -> payment.getTimestamp() != null && payment.getTimestamp().toLocalDate().equals(today))
                .mapToDouble(Payment::getAmount).sum();
        if (chargingRevenue <= 0) chargingRevenue = completed.stream().mapToDouble(Booking::getTotalAmount).sum();
        double refunds = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.REFUNDED)
                .filter(payment -> payment.getTimestamp() != null && payment.getTimestamp().toLocalDate().equals(today))
                .mapToDouble(Payment::getAmount).sum();
        Map<String, Double> byStation = completed.stream().collect(Collectors.groupingBy(Booking::getStationName,
                Collectors.summingDouble(Booking::getTotalAmount)));
        String best = byStation.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("No completed sessions");
        String lowest = byStation.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("No completed sessions");
        return new CompanyAgentResponse.RevenueSummary(completed.size(),
                round(completed.stream().mapToDouble(Booking::getKwhDelivered).sum()), round(chargingRevenue),
                null, null, round(refunds), null, best, lowest);
    }

    private CompanyAgentResponse.PricingRecommendation agentPricingRecommendation(Company company,
            List<ChargingStation> stations) {
        ChargingStation target = stations.stream().filter(station -> station.getStatus() == StationStatus.ACTIVE)
                .min(Comparator.comparingDouble(ChargingStation::getOccupancyPercent)).orElse(null);
        if (target == null) return null;
        double nearbyAverage = stationRepository.findAll().stream()
                .filter(station -> station.getStatus() == StationStatus.ACTIVE && !station.getId().equals(target.getId()))
                .filter(station -> distanceKm(target.getLatitude(), target.getLongitude(), station.getLatitude(), station.getLongitude()) <= 35)
                .mapToDouble(ChargingStation::getPricePerKwh).filter(value -> value > 0).average()
                .orElse(target.getPricePerKwh());
        double desired = target.getOccupancyPercent() < 45 && target.getPricePerKwh() > nearbyAverage
                ? nearbyAverage : target.getPricePerKwh();
        double maximumDelta = target.getPricePerKwh() * company.getAgentMaxPriceChangePercent() / 100.0;
        desired = Math.max(target.getPricePerKwh() - maximumDelta, Math.min(target.getPricePerKwh() + maximumDelta, desired));
        return new CompanyAgentResponse.PricingRecommendation(target.getId(), target.getName(),
                round(target.getPricePerKwh()), round(nearbyAverage), round(desired),
                round(target.getOccupancyPercent()), null, "No scheduled test; reviewed changes persist until updated");
    }

    private List<CompanyAgentResponse.RecommendedAction> agentActions(String intent, Company company,
            CompanyAgentResponse.FaultImpact fault, CompanyAgentResponse.PricingRecommendation pricing) {
        List<CompanyAgentResponse.RecommendedAction> actions = new ArrayList<>();
        boolean approval = true;
        if (("FAULT".equals(intent) || "OVERVIEW".equals(intent)) && fault != null) {
            actions.add(new CompanyAgentResponse.RecommendedAction(CompanyAgentActionType.DISABLE_NEW_BOOKINGS,
                    "Disable new bookings", "LOW", approval || !company.isAgentAutoDisableFaultyChargers(),
                    fault.chargerId(), null, null, fault.issue()));
            actions.add(new CompanyAgentResponse.RecommendedAction(CompanyAgentActionType.CREATE_MAINTENANCE_TICKET,
                    "Create maintenance ticket", "LOW", approval || !company.isAgentAutoCreateMaintenanceTickets(),
                    fault.chargerId(), null, null, fault.issue()));
            actions.add(new CompanyAgentResponse.RecommendedAction(CompanyAgentActionType.NOTIFY_STATION_MANAGER,
                    "Notify station manager", "LOW", approval, fault.chargerId(), null, null, fault.issue()));
        }
        if ("PRICING".equals(intent) && pricing != null
                && Math.abs(pricing.recommendedPricePerKwh() - pricing.currentPricePerKwh()) > 0.009) {
            actions.add(new CompanyAgentResponse.RecommendedAction(CompanyAgentActionType.APPLY_PRICE_RECOMMENDATION,
                    "Apply reviewed station price", "MEDIUM", approval, null, pricing.stationId(),
                    pricing.recommendedPricePerKwh(), "Price change within company limits; persists until changed manually"));
        }
        return actions;
    }

    private String assistantAnswer(String intent, CompanyAgentResponse.NetworkSummary network,
            CompanyAgentResponse.FaultImpact fault, CompanyAgentResponse.RevenueSummary revenue,
            CompanyAgentResponse.PricingRecommendation pricing, List<CompanyAgentResponse.SiteRecommendation> sites,
            Map<String, Object> offerDraft) {
        return switch (intent) {
            case "FAULT" -> fault == null ? "No connector alerts were found in the managed network."
                    : fault.chargerCode() + " at " + fault.stationName() + ": " + fault.issue() + ". " + fault.affectedBookings()
                    + " upcoming station reservations have INR " + fault.estimatedRevenueAtRisk() + " in recorded booking value. Repair time and revenue loss are unknown.";
            case "REVENUE" -> "Today's stored records show " + revenue.sessions() + " completed bookings, " + revenue.energySoldKwh() + " kWh and INR " + revenue.chargingRevenue()
                    + " gross charging receipts/booking amounts. Host payouts, fees and net revenue are not available in this summary.";
            case "PRICING" -> pricing == null ? "No active station has pricing context yet."
                    : pricing.stationName() + " has " + pricing.currentUtilizationPercent() + "% recorded occupancy and INR " + pricing.currentPricePerKwh()
                    + "/kWh pricing. Nearby recorded average: INR " + pricing.nearbyAveragePricePerKwh() + "/kWh. Suggested price for review: INR " + pricing.recommendedPricePerKwh() + "/kWh. Demand uplift is unknown.";
            case "EXPANSION" -> sites.isEmpty() ? "No discoverable, verified property meets the requested power, connector and parking requirements."
                    : sites.get(0).title() + " ranks first at " + sites.get(0).expansionScore() + "/100 using stored bays, load and straight-line network distance. "
                    + sites.get(0).availableLoadKw() + " kW load; " + sites.get(0).parkingBays() + " bays; preliminary setup " + sites.get(0).recommendedChargerCount()
                    + " x " + sites.get(0).recommendedPowerKw() + " kW " + sites.get(0).recommendedConnector() + ". Confirm spare capacity and partnership state before making an offer.";
            case "OFFER" -> "Review this Company's stored proposals in the property workflow. New commercial terms require explicit values and approval.";
            default -> network.activeSessions() + " vehicle(s) are charging now. " + network.available()
                    + " chargers are available, " + network.reserved() + " are reserved, " + network.offline()
                    + " are offline or in maintenance, and " + network.faults() + " need attention.";
        };
    }

    private Map<String, Object> agentOfferDraft(String question,
            List<CompanyAgentResponse.SiteRecommendation> sites) {
        if (sites.isEmpty()) return Map.of();
        CompanyAgentResponse.SiteRecommendation site = sites.stream()
                .filter(candidate -> question.toLowerCase(Locale.ROOT).contains(candidate.title().toLowerCase(Locale.ROOT)))
                .findFirst().orElse(sites.get(0));
        return linkedMap("propertyId", site.propertyId(), "property", site.title(), "location", site.location(),
                "installation", site.recommendedChargerCount() + " x " + site.recommendedPowerKw() + " kW " + site.recommendedConnector(),
                "state", "DRAFT_REQUIRES_TERMS", "missingFields", List.of("Company investment", "Host contribution", "Revenue share or lease", "Term and validity"));
    }

    private boolean operates(ChargingStation station, Company company, Long accountId) {
        if (station.getOperatorCompanyId() != null) return Objects.equals(station.getOperatorCompanyId(), company.getId());
        return Objects.equals(station.getHostUserId(), accountId)
                || Objects.equals(station.getSupplierCompanyId(), company.getId());
    }

    private boolean canonicalDemo(ChargingConnector charger) {
        if (!charger.getStation().isDemoData() || charger.getStation().getDemoSeedKey() == null || charger.getChargerCode() == null) return false;
        String code = charger.getChargerCode();
        Set<String> cities = Set.of("NOIDA", "MATHURA", "AGRA", "GWALIOR", "JHANSI", "LALITPUR", "BINA", "VIDISHA", "BHOPAL");
        return cities.stream().anyMatch(city -> charger.getStation().getDemoSeedKey().equals(city + "_DEMO_01")
                && code.matches("DEMO-" + city + "-(CCS2|TYPE2)-0[12]"));
    }

    private void requireCanonicalDemo(ChargingConnector charger) {
        if (!canonicalDemo(charger)) throw new BadRequestException("Demo controls are restricted to canonical synthetic corridor connectors");
    }

    private Map<String, Object> applyOperationalStatus(Company company, Long accountId, ChargingConnector charger,
            ChargerStatus target, boolean demo, String reason) {
        if (target == ChargerStatus.ONLINE) operationalControlService.assertCompanyPublishingAllowed(accountId);
        if (demo) requireCanonicalDemo(charger);
        charger.setStatus(target);
        charger.setMaintenanceMode(target == ChargerStatus.MAINTENANCE);
        charger.setAvailable(target == ChargerStatus.ONLINE);
        charger.setStatusSource(demo ? "COMPANY_DEMO_CONTROL" : "COMPANY_OPERATOR_CONTROL");
        charger.setStatusChangedAt(LocalDateTime.now());
        charger.setLastHeartbeat(LocalDateTime.now());
        charger.setCurrentPowerKw(0);
        charger.setFaultReason(target == ChargerStatus.ONLINE ? null : reason);
        charger.setFaultCode(target == ChargerStatus.ONLINE ? null : target == ChargerStatus.FAULT
                ? (demo ? "DEMO_CHARGER_FAULT" : "OPERATOR_REPORTED_FAULT") : null);
        if (demo && target == ChargerStatus.ONLINE) charger.setHealthScore(100);
        connectorRepository.save(charger);
        ChargingStation station = charger.getStation();
        boolean healthy = station.getConnectors().stream().anyMatch(CompanyOperatorContextService::online);
        if (!station.isEmergencyDisabled() && station.getStatus() == StationStatus.ACTIVE) {
            station.setAvailability(healthy ? StationAvailability.AVAILABLE : StationAvailability.UNAVAILABLE);
            stationRepository.save(station);
        }
        Map<String, Object> impact = Map.of();
        if (target != ChargerStatus.ONLINE) {
            impact = autopilotService.handleConnectorUnavailable(station.getId(), charger.getType().name(), charger.getId(), reason);
            adminControlService.recordDetectedIncident(station, charger,
                    target == ChargerStatus.FAULT ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH,
                    charger.getStatusSource() + ": " + reason, 0, impact);
        }
        if (demo && target == ChargerStatus.ONLINE) adminControlService.resolveOperatorDemoIncident(station, charger);
        recordActivity(company, accountId, charger.getStatusSource(), "CHARGER", charger.getId(),
                charger.getChargerCode() + " -> " + target + ": " + reason);
        return linkedMap("chargerId", charger.getId(), "chargerCode", charger.getChargerCode(), "status", target,
                "source", charger.getStatusSource(), "syntheticDemo", demo, "stationStillAvailable", healthy, "journeyImpact", impact);
    }

    private List<CompanyAgentResponse.RecommendedAction> demoActions(String question, List<ChargingConnector> chargers) {
        // A name is never enough to grant scope: all candidates already belong to this operator.
        List<ChargingConnector> exact = chargers.stream().filter(this::canonicalDemo)
                .filter(c -> question.contains(c.getChargerCode().toLowerCase(Locale.ROOT))).toList();
        boolean explicitCode = java.util.regex.Pattern.compile("demo-[a-z0-9-]+-\\d+").matcher(question).find();
        if (exact.isEmpty() && !explicitCode && question.contains("agra") && question.contains("demo") && question.contains("ccs2")) {
            exact = chargers.stream().filter(this::canonicalDemo).filter(c -> "DEMO-AGRA-CCS2-01".equals(c.getChargerCode())).toList();
        }
        if (exact.size() != 1) return List.of();
        ChargingConnector c = exact.get(0);
        if (c.getStatus() == ChargerStatus.CHARGING) return List.of();
        CompanyAgentActionType action = question.contains("restore") ? CompanyAgentActionType.RESTORE_DEMO_CHARGER
                : question.contains("maintenance") ? CompanyAgentActionType.PUT_DEMO_CHARGER_IN_MAINTENANCE : CompanyAgentActionType.SIMULATE_DEMO_FAULT;
        String target = action == CompanyAgentActionType.RESTORE_DEMO_CHARGER ? "ONLINE"
                : action == CompanyAgentActionType.PUT_DEMO_CHARGER_IN_MAINTENANCE ? "MAINTENANCE" : "FAULT";
        if (c.getStatus().name().equals(target)) return List.of();
        String reason = target.equals("FAULT") ? "Simulated charger communication failure"
                : target.equals("ONLINE") ? "Operator restored synthetic demo connector" : "Synthetic demo maintenance";
        return List.of(new CompanyAgentResponse.RecommendedAction(action, "Mark " + c.getChargerCode() + " as " + target + "?",
                "HIGH", true, c.getId(), c.getStation().getId(), null,
                reason + ". This may affect active demo journeys. Other healthy connectors remain usable.", c.getStatus()));
    }

    @SuppressWarnings("unchecked")
    private String operatorAnswer(String intent, String question, String fallback, Map<String, Object> operations,
            List<CompanyAgentResponse.RecommendedAction> actions) {
        List<Map<String, Object>> stations = (List<Map<String, Object>>) operations.getOrDefault("stations", List.of());
        if ("DEMO_OPERATION".equals(intent)) return actions.isEmpty()
                ? "No safe demo action was prepared. Specify one exact canonical connector code and check its current status; no state was changed."
                : "DEMO ACTION\n" + actions.get(0).label() + "\n" + actions.get(0).reason() + "\nApproval is required. Nothing has changed.";
        if ("FAULT".equals(intent)) {
            List<Map<String, Object>> priorities = (List<Map<String, Object>>) operations.getOrDefault("maintenancePriorities", List.of());
            return priorities.isEmpty() ? "No operational issues were found in the requested part of your network."
                    : priorities.stream().limit(8).map(r -> r.get("stationName") + " (" + r.get("ownershipType") + ") — "
                    + r.get("issueCount") + " connector(s) need attention; " + r.get("unavailableConnectors") + " unavailable. "
                    + ((List<?>) r.get("affectedJourneyIds")).size() + " affected journey(s); "
                    + ((List<?>) r.get("activeBookingIds")).size() + " active station reservations."
                    + (r.get("propertyTitle") == null ? "" : " Host property: " + r.get("propertyTitle")))
                    .collect(Collectors.joining("\n")) + "\nRanked by severity and stored operational impact. Repair time is unknown.";
        }
        if ("CONNECTOR_AVAILABILITY".equals(intent)) {
            boolean ac = question.contains("only ac") || question.contains("ac charging");
            List<Map<String, Object>> gaps = (List<Map<String, Object>>) operations.getOrDefault(ac ? "acOnlyStations" : "coverageGaps", List.of());
            return gaps.isEmpty() ? (ac ? "No AC-only stations were found in this network selection." : "Every station in this selection has an available CCS2 connector.")
                    : gaps.stream().limit(12).map(r -> r.get("stationName") + " · " + r.get("city") + ": "
                    + r.get("ccs2Connectors") + " CCS2 installed, " + r.get("availableCcs2") + " available").collect(Collectors.joining("\n"));
        }
        if ("OWNERSHIP".equals(intent)) return operations.get("companyOwnedStations") + " Company-owned and " + operations.get("hostPartneredStations") + " Host-partnered stations in this selection.\n" + stations.stream().limit(12).map(r -> r.get("stationName") + ": "
                + r.get("ownershipType") + (r.get("propertyTitle") == null ? "" : " · " + r.get("propertyTitle")))
                .collect(Collectors.joining("\n"));
        if ("OFFER".equals(intent)) {
            List<Map<String, Object>> offers = (List<Map<String, Object>>) operations.getOrDefault("offers", List.of());
            return offers.isEmpty() ? "No persisted offers for the requested property are visible to this Company. Synthetic comparison examples are not saved contracts. Prepare terms in the property workflow; nothing has been sent or accepted."
                    : offers.stream().map(o -> "Request #" + o.get("requestId") + " · " + o.get("property") + " · " + o.get("businessModel")
                    + " · " + o.get("status") + " · " + Objects.toString(o.get("terms"), "No proposal terms stored"))
                    .collect(Collectors.joining("\n")) + "\nOnly your Company's proposals are visible. Review changes in the property workflow.";
        }
        if ("NETWORK".equals(intent)) return fallback + " " + operations.get("healthyStations") + " stations have no recorded connector alerts; "
                + operations.get("hostPartneredStations") + " Host partnerships. Occupancy is a stored snapshot, not a peak-demand forecast.";
        return fallback;
    }

    private CompanyAgentSettingsResponse agentSettingsResponse(Company company) {
        return new CompanyAgentSettingsResponse(company.getAgentMode(), company.getAgentMaxPriceChangePercent(),
                company.isAgentAutoDisableFaultyChargers(), company.isAgentAutoCreateMaintenanceTickets());
    }

    private Long required(Long value, String message) {
        if (value == null) throw new BadRequestException(message);
        return value;
    }

    private String cleanReason(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned == null ? fallback : cleaned;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        if ((lat1 == 0 && lon1 == 0) || (lat2 == 0 && lon2 == 0)) return 50;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public List<Payout> payouts(Long accountId) {
        requireCompany(accountId);
        return payoutRepository.findByHostUserId(accountId);
    }

    private List<ChargingStation> managedStations(Company company, Long accountId) {
        Map<Long, ChargingStation> managed = new LinkedHashMap<>();
        stationRepository.findByOperatorCompanyId(company.getId()).forEach(station -> managed.put(station.getId(), station));
        // Compatibility for records created before operatorCompanyId was introduced.
        stationRepository.findByHostUserId(accountId).forEach(station -> managed.put(station.getId(), station));
        stationRepository.findBySupplierCompanyId(company.getId()).forEach(station -> managed.put(station.getId(), station));
        return managed.values().stream().filter(station -> operates(station, company, accountId)).toList();
    }

    private ChargingConnector managedCharger(Company company, Long accountId, Long chargerId) {
        ChargingConnector charger = connectorRepository.findByIdForUpdate(chargerId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));
        ChargingStation station = charger.getStation();
        if (!operates(station, company, accountId)) {
            throw new ResourceNotFoundException("Charger not found for this company network");
        }
        return charger;
    }

    private boolean needsAttention(ChargingConnector charger) {
        return charger.getStatus() == ChargerStatus.FAULT || charger.getStatus() == ChargerStatus.SUSPECTED_FAULT || charger.getStatus() == ChargerStatus.MAINTENANCE || charger.getStatus() == ChargerStatus.OFFLINE
                || charger.getHealthScore() < 70 || charger.isMaintenanceMode();
    }

    private String relationship(ChargingStation station, Long accountId) {
        return station.getOwnershipType() == StationOwnershipType.HOST_PARTNERED
                || (station.getOwnershipType() == null && station.getHostUserId() != null
                    && !Objects.equals(station.getHostUserId(), accountId))
                ? "HOST_PARTNERED" : "COMPANY_OWNED";
    }

    private ManagedChargerResponse managedChargerResponse(ChargingConnector charger, String relationship) {
        ChargingStation station = charger.getStation();
        return new ManagedChargerResponse(charger.getId(), station.getId(), station.getName(), station.getCity(),
                charger.getChargerCode(), charger.getType().name(), charger.getPowerKw(), charger.getStatus(),
                charger.isAvailable(), charger.isMaintenanceMode(), charger.getHealthScore(), charger.getFaultCode(),
                charger.getLastHeartbeat(), relationship);
    }

    private MaintenanceTicketResponse maintenanceTicketResponse(CompanyMaintenanceTicket ticket) {
        return new MaintenanceTicketResponse(ticket.getId(), ticket.getChargerId(), ticket.getChargerCode(),
                ticket.getStationId(), ticket.getStationName(), ticket.getCity(), ticket.getPriority(), ticket.getStatus(),
                ticket.getIssue(), ticket.getAssignedTo(), ticket.getResolutionNote(), ticket.getCreatedAt(),
                ticket.getUpdatedAt(), ticket.getResolvedAt());
    }

    public byte[] exportReport(Long accountId, String reportType, String format) {
        Map<String, Object> data = "ANALYTICS".equalsIgnoreCase(reportType) ? analytics(accountId) : dashboard(accountId);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Vidyut Company Report", reportType.toUpperCase(Locale.ROOT)));
        rows.add(List.of("Generated", LocalDateTime.now().toString()));
        flattenRows("", data, rows);
        return "PDF".equalsIgnoreCase(format) ? createPdf(rows) : createXlsx(rows);
    }

    private ChargingConnector applyCharger(ChargingConnector connector, ChargerRequest request) {
        connector.setChargerCode(request.getChargerCode().trim().toUpperCase(Locale.ROOT));
        connector.setType(request.getConnectorType());
        connector.setPowerKw(request.getPowerKw());
        connector.setStatus(request.getStatus() == null ? ChargerStatus.ONLINE : request.getStatus());
        connector.setMaintenanceMode(request.isMaintenanceMode());
        connector.setFirmwareVersion(request.getFirmwareVersion() == null ? "1.0.0" : request.getFirmwareVersion());
        connector.setHealthScore(request.getHealthScore());
        connector.setAvailable(connector.getStatus() == ChargerStatus.ONLINE && !connector.isMaintenanceMode());
        connector.setLastHeartbeat(LocalDateTime.now());
        return connector;
    }

    private CompanyEmployee applyEmployee(CompanyEmployee employee, EmployeeRequest request) {
        employee.setName(request.getName().trim());
        employee.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        employee.setPhone(request.getPhone());
        employee.setRole(request.getRole());
        employee.setActive(request.isActive());
        employee.setPermissions(request.getPermissions());
        return employee;
    }

    private void recordActivity(Company company, Long actorAccountId, String action, String resourceType,
                                Long resourceId, String description) {
        activityLogRepository.save(CompanyActivityLog.builder()
                .companyId(company.getId())
                .actorAccountId(actorAccountId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .description(description)
                .build());
    }

    private Company requireCompany(Long accountId) {
        Company company = companyRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ForbiddenException("Company workspace is not available for this account"));
        if (!company.isActive()) throw new ForbiddenException("Company account is disabled");
        return company;
    }

    private Company requireVerifiedCompany(Long accountId) {
        return verificationService.requireMarketplaceVerified(accountId);
    }

    private ChargingStation ownedStation(Long accountId, Long stationId) {
        return ownedStation(requireCompany(accountId), accountId, stationId);
    }

    private ChargingStation ownedStation(Company company, Long accountId, Long stationId) {
        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found for this company"));
        if (!operates(station, company, accountId)) {
            throw new ResourceNotFoundException("Station not found for this company");
        }
        return station;
    }

    private ChargingConnector ownedCharger(Long accountId, Long id) {
        Company company = requireCompany(accountId);
        return managedCharger(company, accountId, id);
    }

    private List<Booking> ownedBookings(Long accountId) {
        Company company = requireCompany(accountId);
        return bookingsForStations(managedStations(company, accountId));
    }

    private List<Booking> bookingsForStations(List<ChargingStation> stations) {
        List<Long> stationIds = stations.stream().map(ChargingStation::getId).toList();
        return stationIds.isEmpty() ? List.of() : bookingRepository.findByStationIdInOrderByStartTimeDesc(stationIds);
    }

    private List<Payment> paymentsForBookings(List<Booking> bookings) {
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        return bookingIds.isEmpty() ? List.of() : paymentRepository.findByBookingIdIn(bookingIds);
    }

    private ChargerResponse mapCharger(ChargingConnector connector) {
        return ChargerResponse.builder().id(connector.getId()).stationId(connector.getStation().getId())
                .stationName(connector.getStation().getName()).chargerCode(connector.getChargerCode())
                .connectorType(connector.getType()).powerKw(connector.getPowerKw()).available(connector.isAvailable())
                .status(connector.getStatus()).maintenanceMode(connector.isMaintenanceMode())
                .firmwareVersion(connector.getFirmwareVersion()).healthScore(connector.getHealthScore())
                .lastHeartbeat(connector.getLastHeartbeat()).faultCode(connector.getFaultCode()).faultReason(connector.getFaultReason())
                .statusSource(connector.getStatusSource()).demoData(canonicalDemo(connector)).build();
    }

    private BookingResponse mapBooking(Booking booking) {
        return BookingResponse.builder().id(booking.getId()).userId(booking.getUserId()).stationId(booking.getStationId())
                .vehicleId(booking.getVehicleId()).stationName(booking.getStationName()).stationAddress(booking.getStationAddress())
                .startTime(booking.getStartTime()).durationHours(booking.getDurationHours()).totalAmount(booking.getTotalAmount())
                .kwhDelivered(booking.getKwhDelivered()).status(booking.getStatus()).createdAt(booking.getCreatedAt()).build();
    }

    private List<Map<String, Object>> alerts(List<ChargingConnector> chargers) {
        return chargers.stream().filter(charger -> charger.getStatus() == ChargerStatus.FAULT
                        || charger.getStatus() == ChargerStatus.OFFLINE || charger.getHealthScore() < 70)
                .sorted(Comparator.comparingInt(ChargingConnector::getHealthScore))
                .limit(10).map(charger -> linkedMap("chargerId", charger.getId(), "chargerCode", charger.getChargerCode(),
                        "station", charger.getStation().getName(), "status", charger.getStatus(), "healthScore", charger.getHealthScore(),
                        "message", charger.getStatus() == ChargerStatus.FAULT ? "Fault detected" : "Charger needs attention"))
                .toList();
    }

    private double revenueSince(List<Booking> bookings, LocalDate start) {
        return round(bookings.stream().filter(booking -> booking.getStatus() == BookingStatus.COMPLETED
                        && booking.getStartTime() != null && !booking.getStartTime().toLocalDate().isBefore(start))
                .mapToDouble(Booking::getTotalAmount).sum());
    }

    private double growth(List<Booking> bookings) {
        long recent = bookings.stream().filter(booking -> booking.getCreatedAt() != null
                && booking.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30))).map(Booking::getUserId).distinct().count();
        long previous = bookings.stream().filter(booking -> booking.getCreatedAt() != null
                && booking.getCreatedAt().isAfter(LocalDateTime.now().minusDays(60))
                && booking.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))).map(Booking::getUserId).distinct().count();
        return previous == 0 ? (recent == 0 ? 0 : 100) : round((recent - previous) * 100.0 / previous);
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> linkedMap(Object... pairs) {
        Map<String, T> map = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) map.put(String.valueOf(pairs[index]), (T) pairs[index + 1]);
        return map;
    }

    private void flattenRows(String prefix, Object value, List<List<String>> rows) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> flattenRows(prefix.isBlank() ? String.valueOf(key) : prefix + "." + key, nested, rows));
        } else if (value instanceof Collection<?> collection) {
            rows.add(List.of(prefix, collection.toString()));
        } else {
            rows.add(List.of(prefix, String.valueOf(value)));
        }
    }

    private byte[] createXlsx(List<List<String>> rows) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zipEntry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
                zipEntry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                zipEntry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Company report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                zipEntry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                for (int r = 0; r < rows.size(); r++) {
                    sheet.append("<row r=\"").append(r + 1).append("\">");
                    for (int c = 0; c < rows.get(r).size(); c++) {
                        sheet.append("<c r=\"").append((char) ('A' + c)).append(r + 1).append("\" t=\"inlineStr\"><is><t>")
                                .append(xml(rows.get(r).get(c))).append("</t></is></c>");
                    }
                    sheet.append("</row>");
                }
                sheet.append("</sheetData></worksheet>");
                zipEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create Excel report", exception);
        }
    }

    private byte[] createPdf(List<List<String>> rows) {
        StringBuilder content = new StringBuilder("BT /F1 10 Tf 50 790 Td 14 TL ");
        for (List<String> row : rows.stream().limit(48).toList()) {
            content.append("(").append(pdf(String.join(": ", row))).append(") Tj T* ");
        }
        content.append("ET");
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + content.toString().getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + content + "\nendstream"
        );
        StringBuilder document = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(document.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            document.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = document.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        document.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) document.append(String.format("%010d 00000 n \n", offsets.get(i)));
        document.append("trailer << /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return document.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private void zipEntry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private String pdf(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?"); }
}
