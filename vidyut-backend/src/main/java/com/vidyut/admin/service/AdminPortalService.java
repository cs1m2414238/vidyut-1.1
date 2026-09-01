package com.vidyut.admin.service;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.admin.dto.*;
import com.vidyut.admin.entity.*;
import com.vidyut.admin.repository.*;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.autopilot.repository.RouteExperienceRepository;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.dto.CompanyVerificationResponse;
import com.vidyut.company.dto.CompanyVerificationReviewRequest;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.repository.CompanyEmployeeRepository;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.service.CompanyVerificationService;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.entity.ProductApprovalStatus;
import com.vidyut.marketplace.repository.ChargerProductRepository;
import com.vidyut.marketplace.repository.InstallationRequestRepository;
import com.vidyut.marketplace.repository.InstallationProposalRepository;
import com.vidyut.payment.entity.PaymentStatus;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPortalService {
    private final AdminAccessService access;
    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final HostProfileRepository hostRepository;
    private final CompanyRepository companyRepository;
    private final CompanyEmployeeRepository companyEmployeeRepository;
    private final CompanyVerificationService companyVerificationService;
    private final LandListingRepository landRepository;
    private final ChargerProductRepository productRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final InstallationRequestRepository installationRepository;
    private final InstallationProposalRepository proposalRepository;
    private final BookingRepository bookingRepository;
    private final ChargingSessionRepository sessionRepository;
    private final PaymentRepository paymentRepository;
    private final AutopilotTripRepository tripRepository;
    private final RouteExperienceRepository experienceRepository;
    private final AdminAccountRepository adminRepository;
    private final AdminAuditLogRepository auditRepository;
    private final AdminAnnouncementRepository announcementRepository;
    private final NetworkIncidentRepository incidentRepository;
    private final AdminSupportCaseRepository supportCaseRepository;
    private final AdminSettlementRepository settlementRepository;
    private final CompanyMaintenanceTicketRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final AdminControlService controlService;
    private final OperationalControlService operationalControlService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public AdminPortalSnapshot snapshot() {
        AdminAccount admin = access.require(AdminCapability.OVERVIEW);
        Set<AdminCapability> caps = access.capabilities(admin.getAdminRole());
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("accounts", accountRepository.count());
        metrics.put("companies", companyRepository.count());
        metrics.put("hosts", hostRepository.count());
        metrics.put("properties", landRepository.count());
        metrics.put("products", productRepository.count());
        metrics.put("stations", stationRepository.count());
        metrics.put("installations", installationRepository.count());
        metrics.put("bookings", bookingRepository.count());
        metrics.put("sessions", sessionRepository.count());
        metrics.put("activeSessions", sessionRepository.findAll().stream()
                .filter(item -> item.getStatus() == ChargingSessionStatus.ACTIVE).count());
        metrics.put("payments", paymentRepository.count());
        metrics.put("autopilotTrips", tripRepository.count());
        metrics.put("routeExperiences", experienceRepository.count());
        var allConnectors = connectorRepository.findAll();
        var allBookings = bookingRepository.findAll();
        var allPayments = paymentRepository.findAll();
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        metrics.put("connectors", (long) allConnectors.size());
        metrics.put("availableChargers", allConnectors.stream().filter(item -> item.isAvailable()
                && item.getStatus() == com.vidyut.station.entity.ChargerStatus.ONLINE && !item.isMaintenanceMode()).count());
        metrics.put("occupiedChargers", allConnectors.stream().filter(item -> item.getStatus() == com.vidyut.station.entity.ChargerStatus.CHARGING).count());
        metrics.put("offlineChargers", allConnectors.stream().filter(item -> item.getStatus() == com.vidyut.station.entity.ChargerStatus.OFFLINE
                || item.getStatus() == com.vidyut.station.entity.ChargerStatus.MAINTENANCE).count());
        metrics.put("faultChargers", allConnectors.stream().filter(item -> item.getStatus() == com.vidyut.station.entity.ChargerStatus.FAULT).count());
        metrics.put("reservedChargers", stationRepository.findAll().stream()
                .filter(item -> item.getAvailability() == com.vidyut.station.entity.StationAvailability.RESERVED).count());
        metrics.put("todayBookings", allBookings.stream().filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(today)).count());
        metrics.put("todayTransactions", allPayments.stream().filter(item -> item.getTimestamp() != null && !item.getTimestamp().isBefore(today)).count());
        metrics.put("platformRevenue", Math.round(allPayments.stream().filter(item -> item.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(item -> item.getAmount() * .05).sum()));
        metrics.put("pendingVerifications", (long) companyVerificationService.reviewQueue().size()
                + hostRepository.findAll().stream().filter(item -> item.getVerificationStatus() != HostVerificationStatus.VERIFIED).count()
                + landRepository.findAll().stream().filter(item -> item.getStatus() == LandListingStatus.PENDING_APPROVAL).count());
        metrics.put("criticalAlerts", incidentRepository.findByStatusInOrderByCreatedAtDesc(
                EnumSet.complementOf(EnumSet.of(IncidentStatus.RESOLVED))).stream()
                .filter(item -> item.getSeverity() == IncidentSeverity.CRITICAL || item.getSeverity() == IncidentSeverity.HIGH).count());

        List<Map<String, Object>> accounts = caps.contains(AdminCapability.ACCOUNTS) ? accountRepository.findAll().stream()
                .map(this::accountView).toList() : List.of();
        List<CompanyVerificationResponse> companies = caps.contains(AdminCapability.VERIFICATIONS)
                ? companyVerificationService.reviewQueue() : List.of();
        List<CompanyVerificationResponse> companyHistory = caps.contains(AdminCapability.VERIFICATIONS)
                ? companyVerificationService.reviewHistory() : List.of();
        List<Map<String, Object>> hosts = caps.contains(AdminCapability.VERIFICATIONS) ? hostRepository.findAll().stream()
                .filter(item -> item.getVerificationStatus() == HostVerificationStatus.PENDING)
                .map(item -> map("accountId", item.getAccountId(), "name", item.getDisplayName(),
                        "email", item.getAccount().getEmail(), "phone", item.getPhone(), "status", item.getVerificationStatus(),
                        "identityType", item.getIdentityType(), "identityLast4", item.getIdentityLast4(),
                        "kycDocumentUrl", item.getKycDocumentUrl(), "requestedAt", item.getVerificationRequestedAt())).toList() : List.of();
        List<Map<String, Object>> properties = caps.contains(AdminCapability.VERIFICATIONS) ? landRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "hostAccountId", item.getHostUserId(), "title", item.getTitle(),
                        "address", item.getAddress(), "city", item.getCity(), "state", item.getState(), "status", item.getStatus(),
                         "ownershipType", item.getOwnershipType(), "ownershipDocumentUrl", item.getOwnershipDocumentUrl(),
                         "electricityDocumentUrl", item.getElectricityDocumentUrl(), "videoVerificationUrl", item.getVideoVerificationUrl(),
                        "discoverable", item.isDiscoverable(), "reviewNote", item.getAdminReviewNote(),
                        "verificationStage", item.getVerificationStage(), "verificationRisk", item.getVerificationRisk(),
                        "verificationMethod", item.getVerificationMethod(), "videoVerified", item.isVideoVerified(),
                        "physicalInspectionStatus", item.getPhysicalInspectionStatus(), "inspectionScheduledAt", item.getInspectionScheduledAt(),
                        "inspectionNote", item.getInspectionNote(), "propertyScore", item.getPropertyScore(),
                        "parking", item.getAvailableParkingBays(), "availableLoadKw", item.getAvailableLoadKw())).toList() : List.of();
        List<Map<String, Object>> products = caps.contains(AdminCapability.VERIFICATIONS) || caps.contains(AdminCapability.OPERATIONS)
                ? productRepository.findAll().stream().map(item -> map("id", item.getId(), "companyId", item.getCompany().getId(),
                        "company", item.getCompany().getCompanyName(), "model", item.getModelName(), "manufacturer", item.getManufacturer(),
                        "connector", item.getConnectorType(), "powerKw", item.getPowerKw(), "approvalStatus", item.getApprovalStatus(),
                        "complianceDocumentUrl", item.getComplianceDocumentUrl(), "reviewNote", item.getAdminReviewNote(),
                        "active", item.isActive())).toList() : List.of();
        List<Map<String, Object>> stations = caps.contains(AdminCapability.OPERATIONS) ? stationRepository.findAll().stream()
                .map(item -> {
                    var operator = item.getOperatorCompanyId() == null ? null
                            : companyRepository.findById(item.getOperatorCompanyId()).orElse(null);
                    boolean companyVerified = operator != null && companyVerificationService.isMarketplaceVerified(operator);
                    boolean hostVerified = item.getHostUserId() == null || hostRepository.findById(item.getHostUserId())
                            .map(HostProfile::isVerified).orElse(false);
                    boolean propertyOwnerVerified = item.getOwnershipType() == com.vidyut.station.entity.StationOwnershipType.HOST_PARTNERED
                            ? hostVerified : companyVerified;
                    boolean siteEvidence = item.getOwnershipType() == com.vidyut.station.entity.StationOwnershipType.HOST_PARTNERED
                            ? item.getHostPartnershipId() != null && installationRepository.findById(item.getHostPartnershipId())
                                .map(installation -> installation.getProperty().getOwnershipDocumentUrl() != null
                                        && installation.getProperty().getElectricityDocumentUrl() != null
                                        && installation.getProperty().getVideoVerificationUrl() != null)
                                .orElse(false)
                            : item.getSiteOwnershipDocumentUrl() != null
                                && item.getElectricityConnectionDocumentUrl() != null;
                    boolean chargersVerified = item.getConnectors() != null && !item.getConnectors().isEmpty()
                            && item.getConnectors().stream().allMatch(connector -> connector.getType() != null
                                && connector.getPowerKw() > 0 && connector.getChargerCode() != null);
                    return map("id", item.getId(), "name", item.getName(), "city", item.getCity(), "status", item.getStatus(),
                            "availability", item.getAvailability(), "ownershipType", item.getOwnershipType(),
                            "propertyOwnerAccountId", item.getPropertyOwnerAccountId(),
                            "propertyOwner", item.getPropertyOwnerName(), "hostAccountId", item.getHostUserId(),
                            "operatorCompanyId", item.getOperatorCompanyId(),
                            "operatorCompany", operator == null ? item.getOperatorCompanyName() : operator.getCompanyName(),
                            "hostPartnershipId", item.getHostPartnershipId(), "companyVerified", companyVerified,
                            "hostVerified", hostVerified, "propertyOwnerVerified", propertyOwnerVerified,
                             "siteEvidence", siteEvidence, "chargersVerified", chargersVerified,
                             "supplierCompanyId", item.getSupplierCompanyId(), "queue", item.getQueueCount(),
                            "occupancy", item.getOccupancyPercent(), "verificationStage", item.getVerificationStage(),
                            "reviewNote", item.getAdminReviewNote(), "reviewedAt", item.getReviewedAt());
                 }).toList() : List.of();
        List<Map<String, Object>> connectors = caps.contains(AdminCapability.OPERATIONS)
                ? controlService.connectorViews() : List.of();
        List<Map<String, Object>> activeSessions = caps.contains(AdminCapability.OPERATIONS) ? sessionRepository.findAll().stream()
                .filter(item -> item.getStatus() == ChargingSessionStatus.ACTIVE)
                .map(item -> map("id", item.getId(), "stationId", item.getStationId(),
                        "station", stationRepository.findById(item.getStationId()).map(com.vidyut.station.entity.ChargingStation::getName).orElse("Station #" + item.getStationId()),
                        "connectorId", item.getConnectorId(),
                        "chargerCode", item.getConnectorId() == null ? null : connectorRepository.findById(item.getConnectorId()).map(com.vidyut.station.entity.ChargingConnector::getChargerCode).orElse(null),
                         "vehicleId", item.getVehicleId(), "battery", item.getCurrentBatteryPercent() + "% → " + item.getTargetBatteryPercent() + "%",
                         "powerKw", item.getPowerKw(), "energyKwh", item.getEnergyKwh(),
                         "startedAt", item.getStartedAt(), "estimatedCompletionAt", item.getEstimatedCompletionAt(),
                        "cost", item.getCost(), "paymentStatus", item.getPaymentStatus(), "status", item.getStatus())).toList() : List.of();
        List<Map<String, Object>> installations = caps.contains(AdminCapability.OPERATIONS) ? installationRepository.findAll().stream()
                .map(item -> {
                    var proposal = proposalRepository.findByRequest_Id(item.getId()).orElse(null);
                    return map("id", item.getId(), "hostAccountId", item.getHostUserId(),
                            "host", hostRepository.findById(item.getHostUserId()).map(HostProfile::getDisplayName).orElse("Host #" + item.getHostUserId()),
                            "property", item.getProperty().getTitle(), "propertyId", item.getProperty().getId(),
                            "company", item.getCompany().getCompanyName(), "companyId", item.getCompany().getId(),
                            "product", item.getProduct().getModelName(), "quantity", item.getQuantity(), "businessModel", item.getBusinessModel(),
                            "budget", item.getBudget(), "status", item.getStatus(), "stationId", item.getStationId(),
                            "surveyAt", item.getScheduledSurveyAt(), "installationAt", item.getScheduledInstallationAt(),
                            "equipmentTotal", proposal == null ? null : proposal.getEquipmentTotal(),
                            "installationTotal", proposal == null ? null : proposal.getInstallationTotal(),
                            "monthlyLease", proposal == null ? null : proposal.getMonthlyLease(),
                            "hostRevenueShare", proposal == null ? null : proposal.getHostRevenueSharePercent(),
                            "companyRevenueShare", proposal == null ? null : proposal.getCompanyRevenueSharePercent(),
                            "validUntil", proposal == null ? null : proposal.getValidUntil(), "terms", proposal == null ? null : proposal.getTerms(),
                            "updatedAt", item.getUpdatedAt());
                }).toList() : List.of();
        List<Map<String, Object>> incidents = caps.contains(AdminCapability.OPERATIONS)
                ? controlService.incidentViews() : List.of();
        List<Map<String, Object>> maintenanceTickets = caps.contains(AdminCapability.OPERATIONS)
                ? controlService.maintenanceViews() : List.of();
        List<Map<String, Object>> settlements = caps.contains(AdminCapability.FINANCE)
                ? controlService.settlementViews() : List.of();
        List<Map<String, Object>> supportCases = caps.contains(AdminCapability.SUPPORT)
                ? controlService.supportViews() : List.of();
        List<Map<String, Object>> greenSchemes = caps.contains(AdminCapability.FINANCE)
                ? controlService.schemeViews() : List.of();
        List<Booking> visibleBookings = caps.contains(AdminCapability.SUPPORT)
                || caps.contains(AdminCapability.OPERATIONS) || caps.contains(AdminCapability.FINANCE)
                ? bookingRepository.findAll() : List.of();
        Map<Long, Booking> bookingsById = visibleBookings.stream()
                .collect(java.util.stream.Collectors.toMap(Booking::getId, item -> item));
        List<Map<String, Object>> bookings = !visibleBookings.isEmpty()
                ? visibleBookings.stream().map(item -> map("id", item.getId(), "userId", item.getUserId(),
                        "station", item.getStationName(), "startTime", item.getStartTime(), "amount", item.getTotalAmount(),
                        "status", item.getStatus(), "refund", item.getRefundAmount())).toList() : List.of();
        List<Map<String, Object>> payments = caps.contains(AdminCapability.FINANCE) ? paymentRepository.findAll().stream()
                .map(item -> {
                    Booking booking = item.getBookingId() == null ? null : bookingsById.get(item.getBookingId());
                    return map("id", item.getId(), "userId", item.getUserId(), "bookingId", item.getBookingId(),
                        "amount", item.getAmount(), "transaction", item.getGatewayTransactionId(), "status", item.getStatus(),
                        "station", booking == null ? "Unlinked payment" : booking.getStationName(),
                        "outletTier", booking == null ? null : booking.getOutletTierName(),
                        "timestamp", item.getTimestamp());
                }).toList() : List.of();
        List<Map<String, Object>> trips = caps.contains(AdminCapability.AI_NETWORK) ? tripRepository.findAll().stream()
                .map(item -> map("id", item.getId(), "userId", item.getUserId(), "origin", item.getOrigin(),
                        "destination", item.getDestination(), "purpose", item.getTripPurpose(), "mode", item.getAutonomyMode(),
                        "status", item.getStatus(), "budget", item.getMaximumChargingBudget(), "cost", item.getEstimatedChargingCost(),
                        "memory", item.getMemorySummary())).toList() : List.of();
        List<Map<String, Object>> network = caps.contains(AdminCapability.AI_NETWORK) ? experienceRepository.findAll().stream()
                .sorted(Comparator.comparing(item -> item.getCreatedAt(), Comparator.reverseOrder())).limit(100)
                .map(item -> map("id", item.getId(), "route", item.getOrigin() + " → " + item.getDestination(),
                        "stationId", item.getStationId(), "outcome", item.getOutcome(), "detail", item.getDetail(),
                        "delayMinutes", item.getDelayMinutes(), "rating", item.getRating(), "createdAt", item.getCreatedAt())).toList() : List.of();
        List<Map<String, Object>> announcements = caps.contains(AdminCapability.SUPPORT) ? announcementRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(item -> map("id", item.getId(), "title", item.getTitle(), "message", item.getMessage(),
                        "audience", item.getAudience(), "severity", item.getSeverity(), "active", item.isActive(),
                        "targetState", item.getTargetState(), "targetCity", item.getTargetCity(),
                        "targetAccountId", item.getTargetAccountId(), "createdAt", item.getCreatedAt())).toList() : List.of();
        return new AdminPortalSnapshot(metrics, accounts, companies, companyHistory, hosts, properties, products, stations, connectors,
                activeSessions, incidents, maintenanceTickets, installations, settlements, supportCases, greenSchemes,
                bookings, payments, trips, network, announcements);
    }

    public List<AdminAuditLog> audits() {
        access.require(AdminCapability.AUDIT);
        return auditRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional
    public CompanyVerificationResponse reviewCompany(Long companyId, CompanyVerificationReviewRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        CompanyVerificationResponse response = companyVerificationService.review(companyId, admin.getAccountId(), request);
        audit(admin, "REVIEW_COMPANY", "COMPANY", companyId, request.status() + ": " + text(request.note()));
        return response;
    }

    @Transactional
    public void reviewHost(Long accountId, AdminReviewNoteRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        HostProfile profile = hostRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Host profile not found"));
        Account account = profile.getAccount();
        if (request.approved()) {
            if (profile.getKycDocumentUrl() == null || profile.getKycDocumentUrl().isBlank()) {
                throw new BadRequestException("Host identity/ownership evidence is required before approval");
            }
            profile.setVerified(true);
            profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
            if (account.getAccountType() == AccountType.INDIVIDUAL) account.getRoles().add(AccountRole.ROLE_HOST);
        } else {
            profile.setVerified(false);
            profile.setVerificationStatus(HostVerificationStatus.REJECTED);
        }
        hostRepository.save(profile);
        accountRepository.save(account);
        audit(admin, "REVIEW_HOST", "HOST", accountId, (request.approved() ? "APPROVED" : "REJECTED") + ": " + text(request.note()));
    }

    @Transactional
    public void reviewProperty(Long id, AdminReviewNoteRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        var property = landRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        boolean hostVerified = hostRepository.findById(property.getHostUserId()).map(HostProfile::isVerified).orElse(false);
        boolean ownershipReady = property.getOwnershipDocumentUrl() != null && !property.getOwnershipDocumentUrl().isBlank();
        boolean electricityReady = property.getElectricityDocumentUrl() != null && !property.getElectricityDocumentUrl().isBlank();
        boolean videoReady = property.getVideoVerificationUrl() != null && !property.getVideoVerificationUrl().isBlank();
        if (request.approved() && (!hostVerified || !ownershipReady || !electricityReady || !videoReady)) {
            throw new BadRequestException("Verified Host identity, ownership/electricity evidence, and a site video are required before publishing this property");
        }
        property.setStatus(request.approved() ? LandListingStatus.APPROVED : LandListingStatus.REJECTED);
        property.setDiscoverable(request.approved());
        property.setVerificationStage(request.approved() ? "PUBLISHED" : "REJECTED");
        if (request.approved()) {
            property.setVideoVerified(true);
            property.setVerificationMethod("DOCUMENT_AND_VIDEO");
        }
        property.setAdminReviewNote(text(request.note()));
        landRepository.save(property);
        audit(admin, "REVIEW_PROPERTY", "PROPERTY", id, property.getStatus() + ": " + text(request.note()));
    }

    @Transactional
    public void reviewProduct(Long id, AdminReviewNoteRequest request) {
        AdminAccount admin = access.require(AdminCapability.VERIFICATIONS);
        var product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Charger product not found"));
        if (request.approved() && (!companyVerificationService.isMarketplaceVerified(product.getCompany())
                || product.getComplianceDocumentUrl() == null || product.getComplianceDocumentUrl().isBlank())) {
            throw new BadRequestException("Vidyut-verified company and product compliance evidence are required");
        }
        product.setApprovalStatus(request.approved() ? ProductApprovalStatus.APPROVED : ProductApprovalStatus.REJECTED);
        product.setActive(request.approved());
        product.setAdminReviewNote(text(request.note()));
        productRepository.save(product);
        audit(admin, "REVIEW_PRODUCT", "CHARGER_PRODUCT", id, product.getApprovalStatus() + ": " + text(request.note()));
    }

    @Transactional
    public void setAccountEnabled(Long id, boolean enabled) {
        AdminAccount admin = access.require(AdminCapability.ACCOUNTS);
        Account account = accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getAccountType() == AccountType.ADMIN) throw new BadRequestException("Use Admin staff management for administrator accounts");
        if (!enabled) {
            throw new BadRequestException("Routine account suspension is disabled. Apply the smallest scoped operational control, or use the Super Admin emergency identity action with a reason.");
        }
        boolean before = account.isEnabled();
        account.setEnabled(enabled);
        accountRepository.save(account);
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId())
                .action(enabled ? "ACTIVATE_ACCOUNT" : "SUSPEND_ACCOUNT").resourceType("ACCOUNT")
                .resourceId(String.valueOf(id)).summary(account.getEmail()).previousValue(String.valueOf(before))
                .newValue(String.valueOf(enabled)).reason("Administrative access control").build());
    }

    @Transactional
    public void cancelBooking(Long id, String note) {
        AdminAccount admin = access.require(AdminCapability.SUPPORT);
        var booking = bookingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (booking.getStatus() == BookingStatus.COMPLETED) throw new BadRequestException("Completed bookings cannot be cancelled");
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        audit(admin, "CANCEL_BOOKING", "BOOKING", id, text(note));
    }

    @Transactional
    public void refundPayment(Long id, String note) {
        AdminAccount admin = access.require(AdminCapability.FINANCE);
        var payment = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getStatus() != PaymentStatus.SUCCESS) throw new BadRequestException("Only successful payments can be refunded");
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        audit(admin, "REFUND_PAYMENT", "PAYMENT", id, text(note));
    }

    @Transactional
    public Map<String, Object> createAnnouncement(AdminAnnouncementRequest request) {
        AdminAccount admin = access.require(AdminCapability.SUPPORT);
        AdminAnnouncement saved = announcementRepository.save(AdminAnnouncement.builder().title(request.title().trim())
                .message(request.message().trim()).audience(request.audience()).severity(request.severity())
                .targetState(text(request.targetState())).targetCity(text(request.targetCity())).targetAccountId(request.targetAccountId())
                .createdByAdminId(admin.getAccountId()).build());
        announcementRecipients(request).forEach(accountId -> notificationService.sendNotification(accountId,
                saved.getTitle(), saved.getMessage(), NotificationType.SYSTEM_ALERT));
        audit(admin, "CREATE_ANNOUNCEMENT", "ANNOUNCEMENT", saved.getId(), saved.getTitle());
        return map("id", saved.getId(), "title", saved.getTitle(), "audience", saved.getAudience(), "severity", saved.getSeverity());
    }

    @Transactional
    public AdminProfileResponse createAdmin(AdminAccountCreateRequest request) {
        AdminAccount actor = access.currentAdmin();
        if (actor.getAdminRole() != AdminRole.SUPER_ADMIN) throw new com.vidyut.common.exception.ForbiddenException("Only a Super Admin can create staff accounts");
        String email = request.email().trim().toLowerCase();
        if (accountRepository.existsByEmailIgnoreCase(email)) throw new DuplicateResourceException("Account already exists with email: " + email);
        Account account = accountRepository.save(Account.builder().email(email).passwordHash(passwordEncoder.encode(request.password()))
                .accountType(AccountType.ADMIN).roles(new LinkedHashSet<>(Set.of(AccountRole.ROLE_ADMIN)))
                .enabled(true).emailVerified(true).build());
        AdminAccount admin = adminRepository.save(AdminAccount.builder().account(account).displayName(request.displayName().trim())
                .adminRole(request.role()).build());
        audit(actor, "CREATE_ADMIN", "ADMIN", account.getId(), email + " as " + request.role());
        return access.profile(admin);
    }

    @Transactional
    public AdminProfileResponse updateAdminRole(Long id, AdminRoleUpdateRequest request) {
        AdminAccount actor = access.currentAdmin();
        if (actor.getAdminRole() != AdminRole.SUPER_ADMIN) throw new com.vidyut.common.exception.ForbiddenException("Only a Super Admin can change staff roles");
        if (actor.getAccountId().equals(id)) throw new BadRequestException("A Super Admin cannot change their own role");
        AdminAccount target = adminRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));
        target.setAdminRole(request.role());
        adminRepository.save(target);
        audit(actor, "CHANGE_ADMIN_ROLE", "ADMIN", id, request.role().name());
        return access.profile(target);
    }

    private void audit(AdminAccount admin, String action, String resource, Object id, String summary) {
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId()).action(action)
                .resourceType(resource).resourceId(id == null ? null : String.valueOf(id))
                .summary(summary == null || summary.isBlank() ? action : summary)
                .reason(summary == null || summary.isBlank() ? action : summary).build());
    }

    private Map<String, Object> accountView(Account item) {
        var userBookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(item.getId());
        var userPayments = paymentRepository.findByUserId(item.getId());
        Map<String, Object> view = map("id", item.getId(), "email", item.getEmail(), "accountType", item.getAccountType(),
                "roles", item.getRoles(), "enabled", item.isEnabled(), "emailVerified", item.isEmailVerified(),
                "createdAt", item.getCreatedAt(), "vehicles", vehicleRepository.findByUserId(item.getId()).size(),
                "bookings", userBookings.size(), "paymentIssues", userPayments.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count(),
                "reportedIncidents", supportCaseRepository.findByAccountIdOrderByUpdatedAtDesc(item.getId()).size());
        evUserProfileRepository.findById(item.getId()).ifPresent(profile -> view.put("displayName", profile.getFullName()));
        view.put("controls", operationalControlService.viewFor(item.getId()));
        companyRepository.findByAccount_Id(item.getId()).ifPresent(company -> {
            var stations = stationRepository.findByOperatorCompanyId(company.getId());
            var stationIds = stations.stream().map(com.vidyut.station.entity.ChargingStation::getId).toList();
            var companyBookings = stationIds.isEmpty() ? List.<Booking>of() : bookingRepository.findByStationIdInOrderByStartTimeDesc(stationIds);
            view.put("displayName", company.getCompanyName());
            view.put("verificationStatus", company.getVerificationStatus());
            view.put("stations", stations.size());
            view.put("chargers", stations.stream().mapToInt(station -> station.getConnectors().size()).sum());
            view.put("employees", companyEmployeeRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).size());
            view.put("transactions", companyBookings.size());
        });
        hostRepository.findById(item.getId()).ifPresent(host -> {
            var properties = landRepository.findByHostUserId(item.getId());
            view.put("displayName", host.getDisplayName());
            view.put("verificationStatus", host.getVerificationStatus());
            view.put("properties", properties.size());
            view.put("verifiedProperties", properties.stream().filter(property -> property.getStatus() == LandListingStatus.APPROVED || property.getStatus() == LandListingStatus.ACTIVE).count());
            view.put("partnerships", installationRepository.findByHostUserIdOrderByUpdatedAtDesc(item.getId()).size());
            view.put("trustScore", Math.round(host.getReputationScore() * 20));
        });
        return view;
    }

    private List<Long> announcementRecipients(AdminAnnouncementRequest request) {
        if (request.targetAccountId() != null) {
            accountRepository.findById(request.targetAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));
            return List.of(request.targetAccountId());
        }
        String state = text(request.targetState()).toLowerCase(Locale.ROOT);
        String city = text(request.targetCity()).toLowerCase(Locale.ROOT);
        return accountRepository.findAll().stream().filter(Account::isEnabled).filter(account -> switch (request.audience()) {
            case "HOST" -> hostRepository.existsById(account.getId());
            case "COMPANY" -> account.getAccountType() == AccountType.COMPANY;
            case "EV_OWNER" -> account.getAccountType() == AccountType.INDIVIDUAL;
            default -> account.getAccountType() != AccountType.ADMIN;
        }).filter(account -> {
            if (state.isBlank() && city.isBlank()) return true;
            String location = companyRepository.findByAccount_Id(account.getId()).map(company -> company.getBusinessAddress())
                    .orElseGet(() -> hostRepository.findById(account.getId()).map(HostProfile::getAddress).orElse(""));
            String normalized = Objects.toString(location, "").toLowerCase(Locale.ROOT);
            return (state.isBlank() || normalized.contains(state)) && (city.isBlank() || normalized.contains(city));
        }).map(Account::getId).toList();
    }

    private String text(String value) { return value == null ? "" : value.trim(); }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
}
