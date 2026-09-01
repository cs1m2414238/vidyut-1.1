package com.vidyut.admin.service;

import com.vidyut.account.entity.Account;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.admin.dto.*;
import com.vidyut.admin.entity.*;
import com.vidyut.admin.repository.*;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.entity.*;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.service.CompanyVerificationService;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.entity.InstallationProposal;
import com.vidyut.marketplace.repository.InstallationProposalRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.entity.Payment;
import com.vidyut.payment.entity.PaymentStatus;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.session.entity.ChargingSession;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminControlService {
    private static final Set<IncidentStatus> ACTIVE_INCIDENTS = EnumSet.complementOf(EnumSet.of(IncidentStatus.RESOLVED));

    private final AdminAccessService access;
    private final AdminAuditLogRepository auditRepository;
    private final AccountRepository accountRepository;
    private final HostProfileRepository hostRepository;
    private final CompanyRepository companyRepository;
    private final CompanyVerificationService companyVerificationService;
    private final LandListingRepository landRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final ChargingSessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final CompanyMaintenanceTicketRepository maintenanceRepository;
    private final NetworkIncidentRepository incidentRepository;
    private final AdminSupportCaseRepository supportRepository;
    private final AdminGreenSchemeRepository schemeRepository;
    private final AdminSettlementRepository settlementRepository;
    private final InstallationProposalRepository proposalRepository;
    private final AutopilotService autopilotService;
    private final NotificationService notificationService;
    private final OperationalControlService operationalControlService;

    @Transactional
    public Map<String, Object> propertyWorkflow(Long propertyId, PropertyWorkflowRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        var property = landRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        String before = property.getVerificationStage();
        String action = request.action();
        String note = clean(request.note());

        switch (action) {
            case "START_REVIEW" -> property.setVerificationStage("UNDER_REVIEW");
            case "REQUEST_INFORMATION" -> property.setVerificationStage("NEEDS_INFORMATION");
            case "REQUEST_VIDEO" -> {
                property.setVerificationStage("VIDEO_REQUIRED");
                property.setVideoVerified(false);
            }
            case "SCHEDULE_INSPECTION" -> {
                if (request.scheduledAt() == null || request.scheduledAt().isBefore(LocalDateTime.now())) {
                    throw new BadRequestException("A future physical-inspection time is required");
                }
                property.setVerificationStage("INSPECTION_SCHEDULED");
                property.setPhysicalInspectionStatus("SCHEDULED");
                property.setInspectionScheduledAt(request.scheduledAt());
                property.setInspectionNote(note);
            }
            case "ESCALATE" -> property.setVerificationStage("ESCALATED");
            case "APPROVE" -> {
                boolean hostVerified = hostRepository.findById(property.getHostUserId()).map(item -> item.isVerified()).orElse(false);
                boolean evidenceReady = present(property.getOwnershipDocumentUrl())
                        && present(property.getElectricityDocumentUrl()) && present(property.getVideoVerificationUrl());
                if (!hostVerified || !evidenceReady) {
                    throw new BadRequestException("Verified Host identity, ownership/electricity evidence, and site video are required before publishing");
                }
                property.setVideoVerified(true);
                property.setVerificationRisk(property.getAvailableLoadKw() >= 200 ? "MEDIUM" : "LOW");
                property.setVerificationMethod("DOCUMENT_AND_VIDEO");
                property.setPropertyScore(propertyScore(property.getAvailableParkingBays(), property.getAvailableLoadKw(), true));
                property.setVerificationStage("PUBLISHED");
                property.setStatus(LandListingStatus.APPROVED);
                property.setDiscoverable(true);
            }
            case "REJECT" -> {
                property.setVerificationStage("REJECTED");
                property.setStatus(LandListingStatus.REJECTED);
                property.setDiscoverable(false);
            }
            default -> throw new BadRequestException("Unsupported property workflow action");
        }
        property.setAdminReviewNote(note);
        landRepository.save(property);
        audit(admin, "PROPERTY_" + action, "PROPERTY", propertyId, before, property.getVerificationStage(), note);
        return map("id", property.getId(), "stage", property.getVerificationStage(), "status", property.getStatus(),
                "discoverable", property.isDiscoverable(), "inspectionScheduledAt", property.getInspectionScheduledAt());
    }

    @Transactional
    public Map<String, Object> reviewStation(Long stationId, StationReviewRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        ChargingStation station = stationRepository.findLockedById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
        String before = station.getVerificationStage();
        String action = request.action();
        switch (action) {
            case "START_REVIEW" -> station.setVerificationStage("UNDER_REVIEW");
            case "REQUEST_INFORMATION" -> station.setVerificationStage("NEEDS_INFORMATION");
            case "ESCALATE" -> station.setVerificationStage("ESCALATED");
            case "VERIFY", "PUBLISH" -> {
                boolean operatorVerified = station.getOperatorCompanyId() != null
                        && companyRepository.findById(station.getOperatorCompanyId())
                        .map(companyVerificationService::isMarketplaceVerified).orElse(false);
                boolean siteReady = station.getOwnershipType() == StationOwnershipType.HOST_PARTNERED
                        ? station.getHostPartnershipId() != null
                        : present(station.getSiteOwnershipDocumentUrl()) && present(station.getElectricityConnectionDocumentUrl());
                boolean chargersReady = station.getConnectors() != null && !station.getConnectors().isEmpty()
                        && station.getConnectors().stream().allMatch(item -> present(item.getChargerCode()) && item.getType() != null && item.getPowerKw() > 0);
                if (!operatorVerified || !siteReady || !chargersReady) {
                    throw new BadRequestException("Verified operator, site-control evidence, and compliant chargers are required");
                }
                station.setVerificationStage("PUBLISH".equals(action) ? "LIVE" : "VERIFIED");
                if ("PUBLISH".equals(action)) {
                    station.setStatus(StationStatus.ACTIVE);
                    station.setEmergencyDisabled(false);
                }
            }
            case "SUSPEND" -> {
                station.setVerificationStage("SUSPENDED");
                station.setStatus(StationStatus.OFFLINE);
                station.setEmergencyDisabled(true);
            }
            default -> throw new BadRequestException("Unsupported station review action");
        }
        station.setAdminReviewNote(clean(request.note()));
        station.setReviewedByAdminId(admin.getAccountId());
        station.setReviewedAt(LocalDateTime.now());
        stationRepository.save(station);
        audit(admin, "STATION_" + action, "STATION", stationId, before, station.getVerificationStage(), request.note());
        return map("id", stationId, "verificationStage", station.getVerificationStage(), "status", station.getStatus());
    }

    @Transactional
    public NetworkIncident createIncident(IncidentCreateRequest request) {
        AdminAccount admin = access.require(AdminCapability.OPERATIONS);
        ChargingConnector connector = connectorRepository.findById(request.connectorId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));
        if (connector.getStation() == null) throw new BadRequestException("Charger is not assigned to a station");
        incidentRepository.findFirstByConnectorIdAndStatusInOrderByCreatedAtDesc(connector.getId(), ACTIVE_INCIDENTS)
                .ifPresent(item -> { throw new BadRequestException("An active incident already exists for this charger"); });

        ChargingStation station = connector.getStation();
        String previous = connector.getStatus().name();
        connector.setStatus(ChargerStatus.FAULT);
        connector.setMaintenanceMode(true);
        connector.setAvailable(false);
        connector.setFaultCode(shortFault(request.reason()));
        connectorRepository.save(connector);
        if (station.getConnectors().stream().noneMatch(item -> item.isAvailable() && item.getStatus() == ChargerStatus.ONLINE && !item.isMaintenanceMode())) {
            station.setAvailability(StationAvailability.UNAVAILABLE);
            stationRepository.save(station);
        }

        Map<String, Object> reroute = autopilotService.handleConnectorUnavailable(station.getId(), connector.getType().name(), connector.getId(), request.reason());
        int automatic = number(reroute.get("automaticReroutes"));
        int approvals = number(reroute.get("driverApprovals"));
        int manual = number(reroute.get("replanRequired"));
        int affected = number(reroute.get("affectedJourneys"));
        Long ticketId = createMaintenanceTicket(station, connector, request);
        IncidentStatus status = manual > 0 ? IncidentStatus.MANUAL_INTERVENTION : IncidentStatus.MONITORING;
        NetworkIncident incident = incidentRepository.save(NetworkIncident.builder()
                .incidentCode("INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .stationId(station.getId()).connectorId(connector.getId()).stationName(station.getName())
                .operatorCompanyId(station.getOperatorCompanyId()).operatorCompanyName(station.getOperatorCompanyName())
                .hostAccountId(station.getHostUserId()).severity(request.severity()).status(status)
                .faultCode(connector.getFaultCode()).description(request.reason()).affectedBookings(affected)
                .usersRerouted(automatic).approvalsRequired(approvals).manualInterventions(manual)
                .estimatedDowntimeMinutes(request.estimatedDowntimeMinutes()).maintenanceTicketId(ticketId).build());
        notifyIncident(station, incident);
        audit(admin, "CREATE_INCIDENT", "INCIDENT", incident.getId(), previous, "FAULT",
                request.reason() + " · rerouted=" + automatic + ", approvals=" + approvals + ", manual=" + manual);
        return incident;
    }

    @Transactional
    public NetworkIncident updateIncident(Long id, IncidentUpdateRequest request) {
        AdminAccount admin = access.require(AdminCapability.OPERATIONS);
        NetworkIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        IncidentStatus before = incident.getStatus();
        incident.setStatus(request.status());
        if (request.estimatedDowntimeMinutes() != null) incident.setEstimatedDowntimeMinutes(request.estimatedDowntimeMinutes());
        incident.setResolutionNote(clean(request.note()));
        if (request.status() == IncidentStatus.RESOLVED) incident.setResolvedAt(LocalDateTime.now());
        NetworkIncident saved = incidentRepository.save(incident);
        audit(admin, "UPDATE_INCIDENT", "INCIDENT", id, before.name(), saved.getStatus().name(), request.note());
        return saved;
    }

    @Transactional
    public NetworkIncident recordDetectedIncident(ChargingStation station, ChargingConnector connector,
                                                   IncidentSeverity severity, String reason,
                                                   int estimatedDowntimeMinutes, Map<String, Object> reroute) {
        Optional<NetworkIncident> existing = incidentRepository
                .findFirstByConnectorIdAndStatusInOrderByCreatedAtDesc(connector.getId(), ACTIVE_INCIDENTS);
        if (existing.isPresent()) return existing.get();
        IncidentCreateRequest request = new IncidentCreateRequest(connector.getId(), severity, reason, estimatedDowntimeMinutes);
        int automatic = number(reroute.get("automaticReroutes"));
        int approvals = number(reroute.get("driverApprovals"));
        int manual = number(reroute.get("replanRequired"));
        int affected = number(reroute.get("affectedJourneys"));
        Long ticketId = createMaintenanceTicket(station, connector, request);
        NetworkIncident incident = incidentRepository.save(NetworkIncident.builder()
                .incidentCode("INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .stationId(station.getId()).connectorId(connector.getId()).stationName(station.getName())
                .operatorCompanyId(station.getOperatorCompanyId()).operatorCompanyName(station.getOperatorCompanyName())
                .hostAccountId(station.getHostUserId()).severity(severity)
                .status(manual > 0 ? IncidentStatus.MANUAL_INTERVENTION : IncidentStatus.MONITORING)
                .faultCode(connector.getFaultCode()).description(reason).affectedBookings(affected)
                .usersRerouted(automatic).approvalsRequired(approvals).manualInterventions(manual)
                .estimatedDowntimeMinutes(estimatedDowntimeMinutes).maintenanceTicketId(ticketId).build());
        notifyIncident(station, incident);
        auditRepository.save(AdminAuditLog.builder()
                .adminAccountId(0L)
                .action("AUTOPILOT_INCIDENT_DETECTED")
                .resourceType("INCIDENT")
                .resourceId(String.valueOf(incident.getId()))
                .summary(incident.getIncidentCode() + " created automatically for " + connector.getChargerCode())
                .previousValue("ONLINE")
                .newValue("FAULT · " + incident.getStatus())
                .reason(reason + " · affected=" + affected + ", rerouted=" + automatic
                        + ", approvals=" + approvals + ", manual=" + manual)
                .build());
        return incident;
    }

    /** Called after Company ownership checks and an approved synthetic connector restoration. */
    @Transactional
    public void resolveOperatorDemoIncident(ChargingStation station, ChargingConnector connector) {
        if (!station.isDemoData() || !"COMPANY_DEMO_CONTROL".equals(connector.getStatusSource())
                || connector.getStatus() != ChargerStatus.ONLINE) throw new BadRequestException("Approved demo restoration required");
        incidentRepository.findFirstByConnectorIdAndStatusInOrderByCreatedAtDesc(connector.getId(), ACTIVE_INCIDENTS)
                .filter(incident -> Objects.equals(incident.getOperatorCompanyId(), station.getOperatorCompanyId()))
                .filter(incident -> incident.getDescription().startsWith("COMPANY_DEMO_CONTROL:"))
                .ifPresent(incident -> {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(LocalDateTime.now());
                    incident.setResolutionNote("Synthetic connector restored by its Company operator");
                    incidentRepository.save(incident);
                    if (incident.getMaintenanceTicketId() != null) maintenanceRepository.findByIdAndCompanyId(
                            incident.getMaintenanceTicketId(), station.getOperatorCompanyId()).ifPresent(ticket -> {
                        ticket.setStatus(MaintenanceTicketStatus.RESOLVED);
                        ticket.setResolvedAt(LocalDateTime.now());
                        ticket.setResolutionNote(incident.getResolutionNote());
                        maintenanceRepository.save(ticket);
                    });
                });
    }

    @Transactional
    public AdminSupportCase createSupportCase(Long accountId, SupportCaseCreateRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return supportRepository.save(AdminSupportCase.builder().accountId(accountId).accountType(account.getAccountType())
                .category(request.category().trim().toUpperCase(Locale.ROOT)).subject(request.subject().trim())
                .description(request.description().trim()).priority(request.priority()).status(SupportCaseStatus.OPEN).build());
    }

    public List<AdminSupportCase> mySupportCases(Long accountId) {
        return supportRepository.findByAccountIdOrderByUpdatedAtDesc(accountId);
    }

    @Transactional
    public AdminSupportCase updateSupportCase(Long id, SupportCaseUpdateRequest request) {
        AdminAccount admin = access.require(AdminCapability.SUPPORT);
        AdminSupportCase item = supportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support case not found"));
        SupportCaseStatus before = item.getStatus();
        item.setStatus(request.status());
        item.setAssignedAdminId(admin.getAccountId());
        if (request.status() == SupportCaseStatus.EVIDENCE_REQUESTED) item.setEvidenceNote(clean(request.note()));
        else item.setResolutionNote(clean(request.note()));
        if (request.status() == SupportCaseStatus.RESOLVED || request.status() == SupportCaseStatus.CLOSED) item.setResolvedAt(LocalDateTime.now());
        AdminSupportCase saved = supportRepository.save(item);
        notificationService.sendNotification(item.getAccountId(), "Support case updated",
                "Case #" + item.getId() + " is now " + item.getStatus().name().replace('_', ' '), NotificationType.SYSTEM_ALERT);
        audit(admin, "UPDATE_SUPPORT_CASE", "SUPPORT_CASE", id, before.name(), saved.getStatus().name(), request.note());
        return saved;
    }

    @Transactional
    public AdminGreenScheme saveScheme(Long id, GreenSchemeRequest request) {
        AdminAccount admin = access.require(AdminCapability.FINANCE);
        AdminGreenScheme item = id == null ? new AdminGreenScheme() : schemeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Green-finance scheme not found"));
        String before = item.getStatus() == null ? "NEW" : item.getStatus().name();
        item.setName(request.name().trim());
        item.setAuthority(request.authority().trim());
        item.setSchemeType(request.schemeType().trim().toUpperCase(Locale.ROOT));
        item.setStates(clean(request.states()));
        item.setSourceUrl(request.sourceUrl().trim());
        item.setSummary(request.summary().trim());
        item.setStatus(request.status());
        item.setValidFrom(request.validFrom());
        item.setValidUntil(request.validUntil());
        item.setLastVerifiedAt(request.status() == GreenSchemeStatus.ACTIVE ? LocalDateTime.now() : item.getLastVerifiedAt());
        if (item.getCreatedByAdminId() == null) item.setCreatedByAdminId(admin.getAccountId());
        AdminGreenScheme saved = schemeRepository.save(item);
        audit(admin, id == null ? "CREATE_GREEN_SCHEME" : "UPDATE_GREEN_SCHEME", "GREEN_SCHEME", saved.getId(), before,
                saved.getStatus().name(), saved.getSourceUrl());
        return saved;
    }

    @Transactional
    public AdminSettlement updateSettlement(Long paymentId, SettlementStatusRequest request) {
        AdminAccount admin = access.require(AdminCapability.FINANCE);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        AdminSettlement settlement = settlementRepository.findByPaymentId(paymentId)
                .orElseGet(() -> calculatedSettlement(payment));
        if (request.status() == SettlementStatus.PAID) {
            operationalControlService.assertSettlementCanBePaid(settlement.getStationId());
        }
        SettlementStatus before = settlement.getStatus();
        settlement.setStatus(request.status());
        settlement.setDisputeNote(clean(request.note()));
        settlement.setProcessedByAdminId(admin.getAccountId());
        if (request.status() == SettlementStatus.PAID || request.status() == SettlementStatus.REFUNDED) settlement.setSettledAt(LocalDateTime.now());
        AdminSettlement saved = settlementRepository.save(settlement);
        audit(admin, "UPDATE_SETTLEMENT", "PAYMENT", paymentId, before.name(), saved.getStatus().name(), request.note());
        return saved;
    }

    public List<Map<String, Object>> connectorViews() {
        access.require(AdminCapability.OPERATIONS);
        Map<Long, ChargingSession> activeByConnector = new HashMap<>();
        sessionRepository.findAll().stream().filter(item -> item.getStatus() == ChargingSessionStatus.ACTIVE && item.getConnectorId() != null)
                .forEach(item -> activeByConnector.put(item.getConnectorId(), item));
        return connectorRepository.findAll().stream().map(item -> {
            ChargingStation station = item.getStation();
            ChargingSession session = activeByConnector.get(item.getId());
            return map("id", item.getId(), "chargerCode", item.getChargerCode(), "stationId", station == null ? null : station.getId(),
                    "stationName", station == null ? null : station.getName(), "city", station == null ? null : station.getCity(),
                    "operatorCompany", station == null ? null : station.getOperatorCompanyName(), "hostAccountId", station == null ? null : station.getHostUserId(),
                    "connectorType", item.getType(), "powerKw", item.getPowerKw(), "status", item.getStatus(), "available", item.isAvailable(),
                    "maintenanceMode", item.isMaintenanceMode(), "healthScore", item.getHealthScore(), "lastHeartbeat", item.getLastHeartbeat(),
                    "faultCode", item.getFaultCode(), "currentPowerKw", item.getCurrentPowerKw(), "sessionEnergyKwh", item.getSessionEnergyKwh(),
                    "sessionId", session == null ? null : session.getId(), "vehicleId", session == null ? null : session.getVehicleId(),
                    "sessionStartedAt", session == null ? null : session.getStartedAt(), "expectedEnd", session == null ? null : session.getEstimatedCompletionAt(),
                    "paymentStatus", session == null ? null : session.getPaymentStatus());
        }).toList();
    }

    public List<Map<String, Object>> incidentViews() {
        access.require(AdminCapability.OPERATIONS);
        return incidentRepository.findAllByOrderByCreatedAtDesc().stream().map(item -> map(
                "id", item.getId(), "incidentCode", item.getIncidentCode(), "stationId", item.getStationId(), "connectorId", item.getConnectorId(),
                "stationName", item.getStationName(), "operatorCompany", item.getOperatorCompanyName(), "hostAccountId", item.getHostAccountId(),
                "severity", item.getSeverity(), "status", item.getStatus(), "faultCode", item.getFaultCode(), "description", item.getDescription(),
                "affectedBookings", item.getAffectedBookings(), "usersRerouted", item.getUsersRerouted(), "approvalsRequired", item.getApprovalsRequired(),
                "manualInterventions", item.getManualInterventions(), "estimatedDowntimeMinutes", item.getEstimatedDowntimeMinutes(),
                "maintenanceTicketId", item.getMaintenanceTicketId(), "resolutionNote", item.getResolutionNote(), "createdAt", item.getCreatedAt(),
                "updatedAt", item.getUpdatedAt())).toList();
    }

    public List<Map<String, Object>> maintenanceViews() {
        access.require(AdminCapability.OPERATIONS);
        LocalDateTime now = LocalDateTime.now();
        return maintenanceRepository.findAllByOrderByUpdatedAtDesc().stream().map(item -> map(
                "id", item.getId(), "companyId", item.getCompanyId(), "chargerId", item.getChargerId(), "chargerCode", item.getChargerCode(),
                "stationId", item.getStationId(), "stationName", item.getStationName(), "city", item.getCity(), "priority", item.getPriority(),
                "status", item.getStatus(), "issue", item.getIssue(), "assignedTo", item.getAssignedTo(), "resolutionNote", item.getResolutionNote(),
                "createdAt", item.getCreatedAt(), "updatedAt", item.getUpdatedAt(), "resolvedAt", item.getResolvedAt(),
                "openMinutes", item.getResolvedAt() == null ? Duration.between(item.getCreatedAt(), now).toMinutes() : Duration.between(item.getCreatedAt(), item.getResolvedAt()).toMinutes()
        )).toList();
    }

    public List<Map<String, Object>> supportViews() {
        access.require(AdminCapability.SUPPORT);
        return supportRepository.findAllByOrderByUpdatedAtDesc().stream().map(item -> map(
                "id", item.getId(), "accountId", item.getAccountId(), "accountType", item.getAccountType(), "category", item.getCategory(),
                "subject", item.getSubject(), "description", item.getDescription(), "priority", item.getPriority(), "status", item.getStatus(),
                "assignedAdminId", item.getAssignedAdminId(), "evidenceNote", item.getEvidenceNote(), "resolutionNote", item.getResolutionNote(),
                "createdAt", item.getCreatedAt(), "updatedAt", item.getUpdatedAt())).toList();
    }

    public List<Map<String, Object>> schemeViews() {
        access.require(AdminCapability.FINANCE);
        return schemeRepository.findAllByOrderByUpdatedAtDesc().stream().map(item -> map(
                "id", item.getId(), "name", item.getName(), "authority", item.getAuthority(), "schemeType", item.getSchemeType(),
                "states", item.getStates(), "sourceUrl", item.getSourceUrl(), "summary", item.getSummary(), "status", item.getStatus(),
                "validFrom", item.getValidFrom(), "validUntil", item.getValidUntil(), "lastVerifiedAt", item.getLastVerifiedAt(), "updatedAt", item.getUpdatedAt())).toList();
    }

    public List<Map<String, Object>> settlementViews() {
        access.require(AdminCapability.FINANCE);
        Map<Long, AdminSettlement> stored = new HashMap<>();
        settlementRepository.findAll().forEach(item -> stored.put(item.getPaymentId(), item));
        return paymentRepository.findAll().stream().map(payment -> settlementMap(stored.getOrDefault(payment.getId(), calculatedSettlement(payment)))).toList();
    }

    public AdminAgentResponse askAgent(AdminAgentRequest request) {
        access.require(AdminCapability.AI_NETWORK);
        String query = request.question().trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> findings = new ArrayList<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        String answer;
        if (query.contains("incident") || query.contains("wrong") || query.contains("fault") || query.contains("offline")) {
            var active = incidentRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_INCIDENTS);
            active.stream().limit(5).forEach(item -> findings.add(map("type", "INCIDENT", "id", item.getId(), "label", item.getIncidentCode(),
                    "detail", item.getStationName() + " · " + item.getDescription(), "severity", item.getSeverity())));
            long faulted = connectorRepository.findAll().stream().filter(item -> item.getStatus() == ChargerStatus.FAULT || item.getStatus() == ChargerStatus.OFFLINE).count();
            answer = active.size() + " active incidents and " + faulted + " fault/offline chargers require network attention.";
            if (faulted > active.size()) actions.add(map("action", "CREATE_INCIDENT", "label", "Create incidents for untracked charger faults", "requiresApproval", true));
        } else if (query.contains("90%") || query.contains("occupancy") || query.contains("busy")) {
            var stations = stationRepository.findAll().stream().filter(item -> item.getOccupancyPercent() >= 90).toList();
            stations.stream().limit(10).forEach(item -> findings.add(map("type", "STATION", "id", item.getId(), "label", item.getName(),
                    "detail", item.getCity() + " · " + item.getOccupancyPercent() + "% occupancy", "severity", "HIGH")));
            answer = stations.size() + " stations are at or above 90% occupancy.";
        } else if (query.contains("downtime") || query.contains("maintenance")) {
            var open = maintenanceRepository.findAllByOrderByUpdatedAtDesc().stream()
                    .filter(item -> item.getStatus() == MaintenanceTicketStatus.OPEN || item.getStatus() == MaintenanceTicketStatus.IN_PROGRESS).toList();
            Map<Long, Long> byCompany = new HashMap<>();
            open.forEach(item -> byCompany.merge(item.getCompanyId(), 1L, Long::sum));
            byCompany.entrySet().stream().sorted(Map.Entry.<Long, Long>comparingByValue().reversed()).limit(5)
                    .forEach(entry -> findings.add(map("type", "COMPANY", "id", entry.getKey(), "label", companyName(entry.getKey()),
                            "detail", entry.getValue() + " open maintenance cases", "severity", entry.getValue() > 2 ? "HIGH" : "MEDIUM")));
            answer = open.size() + " maintenance work orders are still open across the network.";
        } else if (query.contains("settlement") || query.contains("payment") || query.contains("finance")) {
            long failed = paymentRepository.findAll().stream().filter(item -> item.getStatus() == PaymentStatus.FAILED).count();
            long disputed = settlementRepository.findAll().stream().filter(item -> item.getStatus() == SettlementStatus.DISPUTED || item.getStatus() == SettlementStatus.HELD).count();
            findings.add(map("type", "FINANCE", "label", "Payment exceptions", "detail", failed + " failed payments · " + disputed + " disputed/held settlements", "severity", failed + disputed > 0 ? "HIGH" : "LOW"));
            answer = failed + " failed payments and " + disputed + " disputed or held settlements need review.";
        } else {
            long activeSessions = sessionRepository.findAll().stream().filter(item -> item.getStatus() == ChargingSessionStatus.ACTIVE).count();
            long activeIncidents = incidentRepository.findByStatusInOrderByCreatedAtDesc(ACTIVE_INCIDENTS).size();
            long pendingSupport = supportRepository.findAll().stream().filter(item -> item.getStatus() != SupportCaseStatus.CLOSED && item.getStatus() != SupportCaseStatus.RESOLVED).count();
            findings.add(map("type", "NETWORK", "label", "Live network", "detail", activeSessions + " active sessions · " + activeIncidents + " active incidents", "severity", activeIncidents > 0 ? "HIGH" : "LOW"));
            findings.add(map("type", "SUPPORT", "label", "Support queue", "detail", pendingSupport + " cases awaiting closure", "severity", pendingSupport > 0 ? "MEDIUM" : "LOW"));
            answer = "The backend reports " + activeSessions + " active sessions, " + activeIncidents + " active incidents, and " + pendingSupport + " open support cases.";
        }
        return new AdminAgentResponse(answer, findings, actions, "VIDYUT_BACKEND", !actions.isEmpty());
    }

    private Long createMaintenanceTicket(ChargingStation station, ChargingConnector connector, IncidentCreateRequest request) {
        if (station.getOperatorCompanyId() == null) return null;
        if (maintenanceRepository.existsByCompanyIdAndChargerIdAndStatusIn(station.getOperatorCompanyId(), connector.getId(),
                List.of(MaintenanceTicketStatus.OPEN, MaintenanceTicketStatus.IN_PROGRESS))) {
            return maintenanceRepository.findByCompanyIdOrderByUpdatedAtDesc(station.getOperatorCompanyId()).stream()
                    .filter(item -> item.getChargerId().equals(connector.getId())
                            && (item.getStatus() == MaintenanceTicketStatus.OPEN || item.getStatus() == MaintenanceTicketStatus.IN_PROGRESS))
                    .map(CompanyMaintenanceTicket::getId).findFirst().orElse(null);
        }
        MaintenancePriority priority = switch (request.severity()) {
            case CRITICAL -> MaintenancePriority.CRITICAL;
            case HIGH -> MaintenancePriority.HIGH;
            case MEDIUM -> MaintenancePriority.MEDIUM;
            case LOW -> MaintenancePriority.LOW;
        };
        return maintenanceRepository.save(CompanyMaintenanceTicket.builder().companyId(station.getOperatorCompanyId())
                .chargerId(connector.getId()).chargerCode(Objects.toString(connector.getChargerCode(), "CHARGER-" + connector.getId()))
                .stationId(station.getId()).stationName(station.getName()).city(station.getCity()).priority(priority)
                .status(MaintenanceTicketStatus.OPEN).issue(request.reason()).build()).getId();
    }

    private void notifyIncident(ChargingStation station, NetworkIncident incident) {
        String message = incident.getIncidentCode() + " · " + station.getName() + " · " + incident.getDescription();
        if (station.getHostUserId() != null) notificationService.sendNotification(station.getHostUserId(), "Charger incident detected", message, NotificationType.FAULT_ALERT);
        if (station.getOperatorCompanyId() != null) companyRepository.findById(station.getOperatorCompanyId())
                .ifPresent(company -> notificationService.sendNotification(company.getAccount().getId(), "Maintenance incident created", message, NotificationType.FAULT_ALERT));
    }

    private AdminSettlement calculatedSettlement(Payment payment) {
        Booking booking = payment.getBookingId() == null ? null : bookingRepository.findById(payment.getBookingId()).orElse(null);
        ChargingStation station = booking == null ? null : stationRepository.findById(booking.getStationId()).orElse(null);
        double gross = round(payment.getAmount());
        double hostPercent = 0;
        String ownership = station == null || station.getOwnershipType() == null ? "UNLINKED" : station.getOwnershipType().name();
        if (station != null && station.getOwnershipType() == StationOwnershipType.HOST_PARTNERED) {
            hostPercent = Optional.ofNullable(station.getHostPartnershipId()).flatMap(proposalRepository::findByRequest_Id)
                    .map(InstallationProposal::getHostRevenueSharePercent).orElse(15.0);
        }
        double platformPercent = 5.0;
        double host = round(gross * hostPercent / 100.0);
        double platform = round(gross * platformPercent / 100.0);
        double taxes = 0;
        double company = round(Math.max(0, gross - host - platform - taxes));
        SettlementStatus status = switch (payment.getStatus()) {
            case SUCCESS -> SettlementStatus.READY;
            case REFUNDED -> SettlementStatus.REFUNDED;
            case FAILED -> SettlementStatus.HELD;
            case PENDING -> SettlementStatus.PENDING;
        };
        return AdminSettlement.builder().paymentId(payment.getId()).bookingId(payment.getBookingId())
                .stationId(station == null ? null : station.getId()).stationName(station == null ? "Unlinked payment" : station.getName())
                .ownershipType(ownership).grossAmount(gross).platformAmount(platform).companyAmount(company)
                .hostAmount(host).taxesAmount(taxes).status(status).build();
    }

    private Map<String, Object> settlementMap(AdminSettlement item) {
        return map("id", item.getId(), "paymentId", item.getPaymentId(), "bookingId", item.getBookingId(), "stationId", item.getStationId(),
                "stationName", item.getStationName(), "ownershipType", item.getOwnershipType(), "grossAmount", item.getGrossAmount(),
                "platformAmount", item.getPlatformAmount(), "companyAmount", item.getCompanyAmount(), "hostAmount", item.getHostAmount(),
                "taxesAmount", item.getTaxesAmount(), "status", item.getStatus(), "disputeNote", item.getDisputeNote(),
                "updatedAt", item.getUpdatedAt(), "settledAt", item.getSettledAt());
    }

    private void audit(AdminAccount admin, String action, String resource, Object id, String before, String after, String reason) {
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId()).action(action).resourceType(resource)
                .resourceId(id == null ? null : String.valueOf(id)).summary(action.replace('_', ' '))
                .previousValue(clean(before)).newValue(clean(after)).reason(clean(reason)).build());
    }

    private int propertyScore(int parking, double load, boolean verified) {
        return Math.min(100, (verified ? 50 : 0) + Math.min(25, parking * 2) + Math.min(25, (int) Math.round(load / 10)));
    }

    private String companyName(Long id) { return companyRepository.findById(id).map(Company::getCompanyName).orElse("Company #" + id); }
    private String shortFault(String reason) { String value = clean(reason).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"); return value.substring(0, Math.min(120, value.length())); }
    private boolean present(String value) { return value != null && !value.isBlank(); }
    private String clean(String value) { return value == null ? null : value.trim(); }
    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private Map<String, Object> map(Object... pairs) { Map<String, Object> value = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) value.put(String.valueOf(pairs[i]), pairs[i + 1]); return value; }
}
