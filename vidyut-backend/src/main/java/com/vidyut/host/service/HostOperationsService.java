package com.vidyut.host.service;

import com.vidyut.account.entity.*;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.email.service.EmailService;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.admin.entity.GreenSchemeStatus;
import com.vidyut.admin.repository.AdminGreenSchemeRepository;
import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.admin.service.OperationalControlService;
import com.vidyut.agent.service.RoleScopedAgentService;
import com.vidyut.booking.entity.*;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.booking.service.WaitlistService;
import com.vidyut.common.exception.*;
import com.vidyut.host.dto.*;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.repository.HostReviewRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.entity.*;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.payment.repository.PayoutRepository;
import com.vidyut.station.dto.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.*;
import com.vidyut.station.service.ChargingStationService;
import com.vidyut.session.entity.ChargingSession;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.entity.PowerPhase;
import com.vidyut.land.entity.PropertyType;
import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.dto.LandListingResponse;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.land.service.LandListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.zip.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HostOperationsService {
    private final HostProfileRepository hostProfileRepository;
    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final ChargingStationService stationService;
    private final BookingRepository bookingRepository;
    private final WaitlistService waitlistService;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final HostReviewRepository reviewRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AutopilotService autopilotService;
    private final ChargingSessionRepository sessionRepository;
    private final VehicleRepository vehicleRepository;
    private final AdminGreenSchemeRepository greenSchemeRepository;
    private final AdminControlService adminControlService;
    private final OperationalControlService operationalControlService;
    private final RoleScopedAgentService roleScopedAgentService;
    private final com.vidyut.company.repository.CompanyRepository companyRepository;
    private final com.vidyut.company.repository.CompanyMaintenanceTicketRepository maintenanceTicketRepository;
    private final LandListingRepository landListingRepository;
    private final LandListingService landListingService;

    public HostProfileResponse profile(Long accountId) {
        return mapProfile(requireHost(accountId));
    }

    @Transactional
    public HostProfileResponse updateProfile(Long accountId, HostProfileUpdateRequest request) {
        HostProfile profile = requireHost(accountId);
        profile.setDisplayName(request.getDisplayName().trim());
        profile.setPhone(normalizePhone(request.getPhone()));
        profile.setAddress(request.getAddress());
        profile.setBio(request.getBio());
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse submitVerification(Long accountId, HostVerificationRequest request) {
        HostProfile profile = requireHost(accountId);
        profile.setIdentityType(request.getIdentityType().trim().toUpperCase(Locale.ROOT));
        profile.setIdentityLast4(request.getIdentityLast4().trim().toUpperCase(Locale.ROOT));
        profile.setKycDocumentUrl(request.getKycDocumentUrl().trim());
        profile.setVerified(false);
        profile.setVerificationStatus(HostVerificationStatus.PENDING);
        profile.setVerificationRequestedAt(LocalDateTime.now());
        notificationService.sendNotification(accountId, "Host KYC submitted",
                "Your identity documents are queued for verification.", NotificationType.SYSTEM_ALERT);
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse approveVerification(Long accountId) {
        HostProfile profile = requireHost(accountId);
        profile.setVerified(true);
        profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
        notificationService.sendNotification(accountId, "Host KYC verified",
                "You can now register and operate private chargers.", NotificationType.SYSTEM_ALERT);
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse updateBank(Long accountId, HostBankRequest request) {
        HostProfile profile = requireHost(accountId);
        String digits = request.getAccountNumber().replaceAll("\\D", "");
        profile.setBankAccountHolder(request.getAccountHolder().trim());
        profile.setBankName(request.getBankName().trim());
        profile.setBankAccountLast4(digits.substring(Math.max(0, digits.length() - 4)));
        profile.setIfscCode(request.getIfscCode().trim().toUpperCase(Locale.ROOT));
        profile.setPayoutUpi(request.getPayoutUpi());
        profile.setBankVerified(true);
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public HostProfileResponse updateSettings(Long accountId, HostSettingsRequest request) {
        HostProfile profile = requireHost(accountId);
        profile.setEmailNotifications(request.isEmailNotifications());
        profile.setPushNotifications(request.isPushNotifications());
        profile.setAutoAvailability(request.isAutoAvailability());
        return mapProfile(hostProfileRepository.save(profile));
    }

    @Transactional
    public String requestEmailCode(Long accountId) {
        HostProfile profile = requireHost(accountId);
        if (profile.getAccount().isEmailVerified()) {
            return "Email is already verified";
        }

        String code = String.format(
                "%06d",
                new SecureRandom().nextInt(1_000_000));

        profile.setEmailVerificationCodeHash(hash(code));
        profile.setEmailVerificationExpiresAt(
                LocalDateTime.now().plusMinutes(15));

        hostProfileRepository.save(profile);

        emailService.sendVerificationCode(
                profile.getAccount().getEmail(),
                "Host",
                code);

        notificationService.sendNotification(
                accountId,
                "Host email verification",
                "A verification code was sent to your email. It expires in 15 minutes.",
                NotificationType.SYSTEM_ALERT);

        return "Verification code sent to "
                + profile.getAccount().getEmail();
    }

    @Transactional
    public HostProfileResponse confirmEmailCode(Long accountId, String code) {
        HostProfile profile = requireHost(accountId);
        if (profile.getAccount().isEmailVerified())
            return mapProfile(profile);
        if (profile.getEmailVerificationExpiresAt() == null
                || profile.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())
                || !hash(code).equals(profile.getEmailVerificationCodeHash())) {
            throw new BadRequestException("Verification code is invalid or expired");
        }
        profile.getAccount().setEmailVerified(true);
        accountRepository.save(profile.getAccount());
        profile.setEmailVerificationCodeHash(null);
        profile.setEmailVerificationExpiresAt(null);
        return mapProfile(hostProfileRepository.save(profile));
    }

    public List<StationResponse> stations(Long accountId) {
        requireHost(accountId);
        return stationService.getStationsByOwner(accountId);
    }

    @Transactional
    public StationResponse createStation(Long accountId, StationCreateRequest request) {
        requireOperationalHost(accountId);
        if (stationRepository.findByHostUserId(accountId).size() >= 10) {
            throw new BadRequestException("Host accounts can manage up to 10 private charging locations");
        }
        return stationService.createStation(request, accountId);
    }

    @Transactional
    public StationResponse updateStation(Long accountId, Long id, StationUpdateRequest request) {
        requireOperationalHost(accountId);
        assertHostOperates(ownedStation(accountId, id));
        return stationService.updateStation(id, accountId, request);
    }

    @Transactional
    public void deleteStation(Long accountId, Long id) {
        requireOperationalHost(accountId);
        assertHostOperates(ownedStation(accountId, id));
        stationService.deleteStation(id, accountId);
    }

    @Transactional
    public StationResponse updateAvailability(Long accountId, Long stationId, HostAvailabilityRequest request) {
        requireOperationalHost(accountId);
        ChargingStation station = ownedStation(accountId, stationId);
        assertHostOperates(station);
        station.setAvailability(
                request.isEmergencyDisabled() ? StationAvailability.UNAVAILABLE : request.getAvailability());
        station.setEmergencyDisabled(request.isEmergencyDisabled());
        station.setAutoAvailability(request.isAutoAvailability());
        station.setWeeklySchedule(request.getWeeklySchedule());
        station.setHolidaySchedule(request.getHolidaySchedule());
        station.setChargingInstructions(request.getChargingInstructions());
        station.setBookingSlotMinutes(request.getBookingSlotMinutes());
        station.setStatus(request.isEmergencyDisabled() ? StationStatus.OFFLINE : StationStatus.ACTIVE);
        stationRepository.save(station);
        return stationService.getStationById(stationId);
    }

    @Transactional
    public Map<String, Object> updateChargerStatus(Long accountId, Long connectorId, HostChargerStatusRequest request) {
        requireOperationalHost(accountId);
        ChargingConnector connector = ownedConnector(accountId, connectorId);
        assertHostOperates(connector.getStation());
        if (request.getStatus() == ChargerStatus.CHARGING) {
            throw new BadRequestException("Occupied status is controlled by a live charging session, not set manually");
        }
        boolean disruptive = request.getStatus() == ChargerStatus.MAINTENANCE
                || request.getStatus() == ChargerStatus.OFFLINE
                || request.getStatus() == ChargerStatus.FAULT;
        if (disruptive && !request.isImpactApproved()) {
            throw new BadRequestException("Review affected journeys and reservations before changing charger availability");
        }
        if (disruptive) {
            sessionRepository.findFirstByConnectorIdAndStatusOrderByStartedAtDesc(
                    connectorId, ChargingSessionStatus.ACTIVE).ifPresent(session -> {
                session.setStatus(ChargingSessionStatus.COMPLETED);
                session.setPaymentStatus("INTERRUPTED");
                session.setCompletedAt(LocalDateTime.now());
                session.setEstimatedCompletionAt(LocalDateTime.now());
                session.setUpdatedAt(LocalDateTime.now());
                sessionRepository.save(session);
                bookingRepository.findById(session.getBookingId()).ifPresent(booking -> {
                    booking.setStatus(BookingStatus.COMPLETED);
                    booking.setKwhDelivered(session.getEnergyKwh());
                    booking.setTotalAmount(session.getCost());
                    bookingRepository.save(booking);
                });
                if (session.getVehicleId() != null) {
                    vehicleRepository.findById(session.getVehicleId()).ifPresent(vehicle -> {
                        vehicle.setCharging(false);
                        vehicle.setConnectionStatus(com.vidyut.vehicle.entity.VehicleConnectionStatus.DISCONNECTED);
                        vehicle.setTelemetrySource(com.vidyut.vehicle.entity.VehicleTelemetrySource.CHARGING_SESSION);
                        vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
                        vehicleRepository.save(vehicle);
                    });
                }
            });
        }
        connector.setStatus(request.getStatus());
        connector.setAvailable(request.getStatus() == ChargerStatus.ONLINE);
        connector.setMaintenanceMode(request.getStatus() == ChargerStatus.MAINTENANCE);
        connector.setCurrentPowerKw(request.getCurrentPowerKw());
        connector.setSessionEnergyKwh(request.getSessionEnergyKwh());
        connector.setHealthScore(request.getHealthScore());
        connector.setFaultCode(request.getFaultCode());
        connector.setLastHeartbeat(LocalDateTime.now());
        if (request.getStatus() == ChargerStatus.CHARGING && connector.getSessionStartedAt() == null)
            connector.setSessionStartedAt(LocalDateTime.now());
        if (request.getStatus() != ChargerStatus.CHARGING)
            connector.setSessionStartedAt(null);
        connectorRepository.save(connector);
        ChargingStation station = connector.getStation();
        boolean stationHasLiveConnector = station.getConnectors().stream().anyMatch(candidate ->
                candidate.isAvailable() && !candidate.isMaintenanceMode()
                        && candidate.getStatus() == ChargerStatus.ONLINE);
        if (stationHasLiveConnector) {
            station.setStatus(StationStatus.ACTIVE);
            station.setAvailability(StationAvailability.AVAILABLE);
            station.setEmergencyDisabled(false);
        } else {
            station.setStatus(request.getStatus() == ChargerStatus.MAINTENANCE
                    ? StationStatus.MAINTENANCE : StationStatus.OFFLINE);
            station.setAvailability(StationAvailability.UNAVAILABLE);
        }
        stationRepository.save(station);
        Map<String, Object> networkResult = disruptive
                ? autopilotService.handleConnectorUnavailable(
                        station.getId(), connector.getType().name(), connector.getId(),
                        "The station host approved " + request.getStatus().name().toLowerCase(Locale.ROOT) + ".")
                : Map.of("affectedJourneys", 0, "automaticReroutes", 0, "driverApprovals", 0,
                        "replanRequired", 0, "backupConnectorAvailable", false);
        if (disruptive) {
            adminControlService.recordDetectedIncident(station, connector,
                    request.getStatus() == ChargerStatus.FAULT ? IncidentSeverity.CRITICAL : IncidentSeverity.HIGH,
                    "Host reported " + Objects.toString(request.getFaultCode(), request.getStatus().name()),
                    request.getStatus() == ChargerStatus.FAULT ? 180 : 120, networkResult);
        }
        if (request.getStatus() == ChargerStatus.FAULT) {
            notificationService.sendNotification(accountId, "Charger fault detected",
                    connector.getChargerCode() + " reported " + Objects.toString(request.getFaultCode(), "a fault"),
                    NotificationType.FAULT_ALERT);
        }
        if (disruptive) {
            notificationService.sendNotification(accountId, "Maintenance change propagated",
                    networkResult.get("automaticReroutes") + " journey rerouted automatically · "
                            + networkResult.get("driverApprovals") + " driver approval(s) requested.",
                    NotificationType.SYSTEM_ALERT);
        }
        Map<String, Object> response = new LinkedHashMap<>(mapConnector(connector));
        response.put("networkResult", networkResult);
        return response;
    }

    public Map<String, Object> maintenanceImpact(Long accountId, Long connectorId) {
        requireOperationalHost(accountId);
        ChargingConnector connector = ownedConnector(accountId, connectorId);
        Map<String, Object> journeyImpact = autopilotService.connectorDisruptionImpact(
                connector.getStation().getId(), connector.getType().name(), connector.getId());
        long upcomingReservations = bookingRepository.findByStationId(connector.getStation().getId()).stream()
                .filter(booking -> booking.getStatus() == BookingStatus.PENDING
                        || booking.getStatus() == BookingStatus.CONFIRMED
                        || booking.getStatus() == BookingStatus.IN_PROGRESS)
                .count();
        List<Map<String, Object>> alternatives = alternativeStationScenarios(connector);
        Map<String, Object> bestAlternative = alternatives.isEmpty() ? null : alternatives.get(0);
        double repairEstimate = connector.getPowerKw() >= 100 ? 4_500 : 2_800;
        double lostRevenueNextThreeHours = connector.getStation().isDemoData()
                ? 1_840 : round(Math.max(1, upcomingReservations) * 420);
        double revenueLoss24Hours = connector.getStation().isDemoData()
                ? 6_700 : round(lostRevenueNextThreeHours * 2.8);
        int affectedUsers = ((Number) journeyImpact.get("activeJourneys")).intValue()
                + Math.toIntExact(upcomingReservations);
        return linkedMap(
                "connectorId", connector.getId(),
                "chargerCode", connector.getChargerCode(),
                "stationId", connector.getStation().getId(),
                "stationName", connector.getStation().getName(),
                "operatorCompanyName", Objects.toString(connector.getStation().getOperatorCompanyName(), "Host operated"),
                "canControlOperationalStatus", connector.getStation().getOperatorCompanyId() == null && connector.getStation().getSupplierCompanyId() == null,
                "faultCode", Objects.toString(connector.getFaultCode(), "COOLING_SYSTEM_TEMP_HIGH"),
                "estimatedRepairHours", connector.getPowerKw() >= 100 ? 3 : 2,
                "repairEstimate", repairEstimate,
                "estimatedLostRevenueNext3Hours", lostRevenueNextThreeHours,
                "estimatedRevenueLoss24Hours", revenueLoss24Hours,
                "repairRecommendation", repairEstimate < revenueLoss24Hours
                        ? "REPAIR_NOW" : "COMPARE_REPAIR_AND_REPLACEMENT",
                "activeJourneys", journeyImpact.get("activeJourneys"),
                "automaticReroutes", journeyImpact.get("automaticReroutes"),
                "driverApprovals", journeyImpact.get("driverApprovals"),
                "upcomingReservations", upcomingReservations,
                "backupConnectorAvailable", journeyImpact.get("backupConnectorAvailable"),
                "affectedUsers", affectedUsers,
                "recommendedAlternatives", alternatives,
                "modeledUserImpact", bestAlternative == null
                        ? linkedMap("extraDistanceKm", 0, "delayMinutes", 0, "chargingCostDifference", 0,
                                "extraBatteryPercent", 0, "dataBasis", "NO_SAFE_ALTERNATIVE")
                        : linkedMap("extraDistanceKm", bestAlternative.get("extraDistanceKm"),
                                "delayMinutes", bestAlternative.get("delayMinutes"),
                                "chargingCostDifference", bestAlternative.get("chargingCostDifference"),
                                "extraBatteryPercent", bestAlternative.get("extraBatteryPercent"),
                                "dataBasis", "COMPATIBLE_STATION_DEMO_ESTIMATE"),
                "message", Boolean.TRUE.equals(journeyImpact.get("backupConnectorAvailable"))
                        ? "Another compatible connector can protect current journey reservations."
                        : "Affected journeys will be replanned immediately after approval."
        );
    }

    private List<Map<String, Object>> alternativeStationScenarios(ChargingConnector unavailable) {
        ChargingStation source = unavailable.getStation();
        double sourceSessionCost = source.getPricePerKwh() * 21;
        return stationRepository.findCompatibleAlternativeStations(
                        source.getId(), StationStatus.ACTIVE, StationAvailability.UNAVAILABLE,
                        unavailable.getType(), ChargerStatus.ONLINE,
                        source.getLatitude() - 1.5, source.getLatitude() + 1.5,
                        source.getLongitude() - 1.5, source.getLongitude() + 1.5).stream()
                .<Map<String, Object>>map(station -> station.getConnectors().stream()
                        .filter(connector -> connector.getType() == unavailable.getType()
                                && connector.getStatus() == ChargerStatus.ONLINE
                                && connector.isAvailable() && !connector.isMaintenanceMode())
                        .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                        .map(connector -> {
                            double directDistance = haversineKm(source.getLatitude(), source.getLongitude(),
                                    station.getLatitude(), station.getLongitude());
                            double extraDistance = round(Math.max(1.2, directDistance * .35));
                            int waitMinutes = Math.max(0, station.getQueueCount() * 4);
                            int delayMinutes = Math.max(3, (int) Math.ceil(extraDistance / 52 * 60) + waitMinutes);
                            double chargingCost = round(station.getPricePerKwh() * 21);
                            double costDifference = round(chargingCost - sourceSessionCost);
                            double battery = round(extraDistance * .38);
                            double score = delayMinutes + Math.max(0, costDifference) * .2
                                    + Math.max(0, 100 - connector.getHealthScore()) * .15;
                            return linkedMap("stationId", station.getId(), "stationName", station.getName(),
                                    "operatorCompanyName", Objects.toString(station.getOperatorCompanyName(), "Vidyut discovery network"),
                                    "connectorType", connector.getType(), "powerKw", connector.getPowerKw(),
                                    "extraDistanceKm", extraDistance, "waitMinutes", waitMinutes,
                                    "chargingCost", chargingCost, "chargingCostDifference", costDifference,
                                    "delayMinutes", delayMinutes, "extraBatteryPercent", battery,
                                    "availabilityRisk", Math.max(0, 100 - connector.getHealthScore()),
                                    "score", round(score), "demoScenario", true);
                        }).orElse(null))
                .filter(Objects::nonNull)
                .filter(item -> ((Number) item.get("extraDistanceKm")).doubleValue() <= 45)
                .sorted(Comparator.comparingDouble(item -> ((Number) item.get("score")).doubleValue()))
                .limit(3)
                .toList();
    }

    public List<HostBookingResponse> bookings(Long accountId) {
        requireHost(accountId);
        return ownedBookings(accountId).stream().map(this::mapBooking).toList();
    }

    @Transactional
    public HostBookingResponse updateBooking(Long accountId, Long bookingId, BookingStatus status) {
        requireOperationalHost(accountId);
        Booking booking = ownedBooking(accountId, bookingId);
        if (status == booking.getStatus()) return mapBooking(booking);
        if (!isAllowedHostTransition(booking.getStatus(), status)) {
            throw new BadRequestException("Booking cannot move from " + booking.getStatus() + " to " + status);
        }
        booking.setStatus(status);
        bookingRepository.save(booking);
        if (status == BookingStatus.CANCELLED) waitlistService.promoteNext(booking.getStationId());
        NotificationType type = switch (status) {
            case CANCELLED -> NotificationType.BOOKING_CANCELLED;
            case IN_PROGRESS -> NotificationType.CHARGING_STARTED;
            case COMPLETED -> NotificationType.CHARGING_COMPLETED;
            default -> NotificationType.BOOKING_CONFIRMED;
        };
        notificationService.sendNotification(booking.getUserId(), "Booking " + status.name().toLowerCase(Locale.ROOT),
                booking.getStationName() + " · " + booking.getStartTime(), type);
        if (status == BookingStatus.COMPLETED) {
            notificationService.sendNotification(accountId, "Payment received",
                    "₹" + round(booking.getTotalAmount()) + " earned from booking #" + booking.getId(),
                    NotificationType.PAYMENT_RECEIVED);
        }
        return mapBooking(booking);
    }

    @Transactional
    public HostBookingResponse reschedule(Long accountId, Long bookingId, LocalDateTime startTime) {
        requireOperationalHost(accountId);
        Booking booking = ownedBooking(accountId, bookingId);
        booking.setStartTime(startTime);
        bookingRepository.save(booking);
        notificationService.sendNotification(booking.getUserId(), "Booking rescheduled",
                "Your charging slot now starts at " + startTime, NotificationType.BOOKING_CONFIRMED);
        return mapBooking(booking);
    }

    public Map<String, Object> dashboard(Long accountId) {
        HostProfile profile = requireHost(accountId);
        List<ChargingStation> stations = stationRepository.findByHostUserId(accountId);
        List<ChargingConnector> connectors = connectorRepository.findByStation_HostUserId(accountId);
        List<Booking> bookings = bookingsFor(stations);
        Map<String, Object> earnings = earnings(accountId);
        long upcoming = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED)
                .count();
        long active = bookings.stream().filter(b -> b.getStatus() == BookingStatus.IN_PROGRESS).count();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long monthlySessions = bookings.stream().filter(b -> b.getStartTime() != null
                && !b.getStartTime().isBefore(monthStart)
                && (b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CANCELLED)).count();
        long completedSessions = bookings.stream().filter(b -> b.getStartTime() != null
                && !b.getStartTime().isBefore(monthStart) && b.getStatus() == BookingStatus.COMPLETED).count();
        double successfulSessionsPercent = monthlySessions == 0 ? 0 : round(completedSessions * 100.0 / monthlySessions);
        double energy = bookings.stream().mapToDouble(Booking::getKwhDelivered).sum();
        double uptime = connectors.isEmpty() ? 0
                : round(connectors.stream()
                        .filter(c -> c.getStatus() == ChargerStatus.ONLINE || c.getStatus() == ChargerStatus.CHARGING)
                        .count() * 100.0 / connectors.size());
        return linkedMap("displayName", profile.getDisplayName(), "verified", profile.isVerified(),
                "totalLocations", stations.size(),
                "totalChargers", connectors.size(), "onlineChargers",
                connectors.stream().filter(c -> c.getStatus() == ChargerStatus.ONLINE).count(),
                "occupiedChargers", connectors.stream().filter(c -> c.getStatus() == ChargerStatus.CHARGING).count(),
                "activeSessions", active, "upcomingBookings", upcoming, "monthlySessions", monthlySessions,
                "successfulSessionsPercent", successfulSessionsPercent, "energyDeliveredKwh", round(energy),
                "uptimePercent", uptime,
                "todayEarnings", earnings.get("daily"), "monthlyEarnings", earnings.get("monthly"), "pendingPayout",
                earnings.get("pendingPayout"),
                "reputationScore", profile.getReputationScore(), "alerts",
                monitoring(accountId).stream().filter(item -> "FAULT".equals(item.get("status"))
                        || ((Number) item.get("healthScore")).intValue() < 70).toList());
    }

    public Map<String, Object> earnings(Long accountId) {
        requireHost(accountId);
        List<Booking> bookings = ownedBookings(accountId);
        List<Booking> completed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).toList();
        List<Booking> completedToday = completed.stream()
                .filter(booking -> booking.getStartTime() != null
                        && booking.getStartTime().toLocalDate().equals(LocalDate.now()))
                .toList();
        double daily = revenueSince(completed, LocalDate.now());
        double weekly = revenueSince(completed, LocalDate.now().minusDays(6));
        double monthly = revenueSince(completed, LocalDate.now().withDayOfMonth(1));
        double total = completed.stream().mapToDouble(Booking::getTotalAmount).sum();
        List<Payout> payouts = payoutRepository.findByHostUserId(accountId);
        double withdrawn = payouts.stream().mapToDouble(Payout::getAmount).sum();
        double available = Math.max(0, total - withdrawn);
        double pending = payouts.stream().filter(p -> "PENDING".equalsIgnoreCase(p.getStatus()))
                .mapToDouble(Payout::getAmount).sum();
        double dailyEnergy = completedToday.stream().mapToDouble(Booking::getKwhDelivered).sum();
        double electricityCost = round(dailyEnergy * 8.5);
        double platformShare = round(daily * .08);
        double hostNet = round(Math.max(0, daily - electricityCost - platformShare));
        double averageSession = completedToday.isEmpty() ? 0 : round(daily / completedToday.size());
        Map<Integer, Long> hourlyDemand = new HashMap<>();
        completed.stream().filter(booking -> booking.getStartTime() != null)
                .forEach(booking -> hourlyDemand.merge(booking.getStartTime().getHour(), 1L, Long::sum));
        int peakHour = hourlyDemand.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(18);
        int connectorCount = Math.max(1, connectorRepository.findByStation_HostUserId(accountId).size());
        double occupiedMinutes = completedToday.stream().mapToInt(booking -> booking.getDurationMinutes() > 0
                ? booking.getDurationMinutes() : Math.max(1, booking.getDurationHours()) * 60).sum();
        double utilization = round(Math.min(100, occupiedMinutes * 100.0 / (connectorCount * 24 * 60)));
        int year = LocalDate.now().getMonthValue() >= 4 ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1;
        return linkedMap("daily", round(daily), "weekly", round(weekly), "monthly", round(monthly), "lifetime",
                round(total),
                "availableBalance", round(available), "pendingPayout", round(pending), "taxWithheld",
                round(total * .01),
                "financialYear", year + "-" + String.valueOf(year + 1).substring(2), "payouts", payouts,
                "todayGrossRevenue", round(daily), "todayElectricityCost", electricityCost,
                "todayPlatformShare", platformShare, "todayHostNet", hostNet,
                "todaySessions", completedToday.size(), "averageSessionValue", averageSession,
                "utilizationPercent", utilization,
                "peakWindow", String.format("%02d:00–%02d:00", peakHour, Math.min(24, peakHour + 3)),
                "transactions",
                completed.stream().map(b -> linkedMap("bookingId", b.getId(), "station", b.getStationName(), "amount",
                        b.getTotalAmount(), "timestamp", b.getStartTime(), "status", "EARNED")).toList());
    }

    @Transactional
    public Payout withdraw(Long accountId, double amount) {
        operationalControlService.assertHostPayoutAllowed(accountId);
        HostProfile profile = requireOperationalHost(accountId);
        if (!profile.isBankVerified())
            throw new ForbiddenException("Verify a bank account before withdrawing earnings");
        double available = ((Number) earnings(accountId).get("availableBalance")).doubleValue();
        if (amount > available)
            throw new BadRequestException("Withdrawal exceeds available balance");
        Payout payout = payoutRepository
                .save(Payout.builder().hostUserId(accountId).amount(round(amount)).status("PENDING").build());
        notificationService.sendNotification(accountId, "Withdrawal requested",
                "₹" + round(amount) + " will be processed to bank account ending " + profile.getBankAccountLast4(),
                NotificationType.SYSTEM_ALERT);
        return payout;
    }

    public List<Map<String, Object>> monitoring(Long accountId) {
        requireHost(accountId);
        List<ChargingConnector> connectors = connectorRepository.findByStation_HostUserId(accountId);
        List<Long> stationIds = connectors.stream().map(connector -> connector.getStation().getId()).distinct().toList();
        Map<Long, ChargingSession> activeByConnector = stationIds.isEmpty() ? Map.of()
                : sessionRepository.findByStationIdInAndStatusOrderByStartedAtDesc(
                                stationIds, ChargingSessionStatus.ACTIVE).stream()
                        .filter(session -> session.getConnectorId() != null)
                        .collect(java.util.stream.Collectors.toMap(ChargingSession::getConnectorId,
                                session -> session, (newest, ignored) -> newest, LinkedHashMap::new));
        return connectors.stream().map(connector -> mapConnector(connector, activeByConnector.get(connector.getId()))).toList();
    }

    public List<HostReview> reviews(Long accountId) {
        requireHost(accountId);
        return reviewRepository.findByHostAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional
    public HostReview replyReview(Long accountId, Long reviewId, String reply) {
        requireHost(accountId);
        HostReview review = ownedReview(accountId, reviewId);
        review.setHostReply(reply.trim());
        return reviewRepository.save(review);
    }

    @Transactional
    public HostReview reportReview(Long accountId, Long reviewId, String reason) {
        requireHost(accountId);
        HostReview review = ownedReview(accountId, reviewId);
        review.setReported(true);
        review.setReportReason(reason.trim());
        return reviewRepository.save(review);
    }

    public Map<String, Object> assistant(Long accountId, String rawQuestion) {
        return assistant(accountId, rawQuestion, null);
    }

    public Map<String, Object> assistant(Long accountId, String rawQuestion, String authorization) {
        Map<String, Object> dashboard = dashboard(accountId);
        Map<String, Object> earnings = earnings(accountId);
        List<Booking> bookings = ownedBookings(accountId);
        List<ChargingStation> stations = stationRepository.findByHostUserId(accountId);
        List<ChargingConnector> connectors = connectorRepository.findByStation_HostUserId(accountId);
        List<Map<String, Object>> liveSessions = monitoring(accountId).stream()
                .filter(item -> "CHARGING".equals(item.get("status"))).toList();
        Map<Integer, Long> hours = new HashMap<>();
        bookings.stream().filter(b -> b.getStartTime() != null)
                .forEach(b -> hours.merge(b.getStartTime().getHour(), 1L, Long::sum));
        int peak = hours.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(18);
        String q = rawQuestion.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> maintenance = maintenanceRisks(accountId, connectors, bookings);
        Map<String, Object> operatingHours = operatingHoursRecommendation(stations, bookings, earnings, peak);
        List<Map<String, Object>> companyDeals = companyDealScenarios(earnings);
        Map<String, Object> solar = solarOpportunity(stations, bookings);
        List<Map<String, Object>> actions = new ArrayList<>();
        ChargingStation busiest = busiestStation(stations, bookings);
        Map<String, Object> highestRisk = maintenance.isEmpty() ? null : maintenance.get(0);
        if (highestRisk != null && ((Number) highestRisk.get("riskScore")).intValue() >= 35) {
            String op = Objects.toString(highestRisk.get("operatorCompanyName"), "Tata Power — Demo Operator Data");
            actions.add(linkedMap("action", "REQUEST_TATA_SERVICE",
                    "label", "Request service from " + (op.contains("Tata") ? "Tata" : "operator"),
                    "requiresConfirmation", true,
                    "connectorId", highestRisk.get("connectorId"), "stationId", highestRisk.get("stationId"),
                    "detail", "Send an urgent service notification and work order request to " + op + "."));
            actions.add(linkedMap("action", "PUT_CONNECTOR_IN_MAINTENANCE",
                    "label", "Isolate connector", "requiresConfirmation", true,
                    "connectorId", highestRisk.get("connectorId"), "stationId", highestRisk.get("stationId"),
                    "detail", "Impact-check active journeys, then isolate this connector."));
        }
        if (busiest != null) {
            actions.add(linkedMap("action", "EXTEND_HOURS", "label", "Extend tomorrow to midnight",
                    "requiresConfirmation", true, "stationId", busiest.getId(),
                    "detail", operatingHours.get("estimatedAdditionalRevenue") + " estimated gross opportunity."));
        }
        actions.add(linkedMap("action", "OPEN_MARKETPLACE", "label", "Compare company offers",
                "requiresConfirmation", false, "detail", "Open verified marketplace opportunities; no contract will be signed."));
        if (!stations.isEmpty()) {
            actions.add(linkedMap("action", "PREPARE_GREEN_FINANCE", "label", "Prepare Green Finance checklist",
                    "requiresConfirmation", true, "stationId", solar.get("stationId"),
                    "detail", "Prepare inputs and documents only; submission always needs separate approval."));
        }

        Map<String, Object> readinessData = evaluatePropertyReadiness(accountId);
        Map<String, Object> offersData = compareCompanyOffers(accountId, q);
        Map<String, Object> chargerHealthData = getHostedChargerHealth(accountId);

        String answer;
        if (q.contains("which property") || q.contains("expansion") || q.contains("best property")
                || q.contains("expand") || q.contains("another charger") || q.contains("expansion potential")) {
            answer = String.valueOf(readinessData.get("topRecommendation"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rankedList = (List<Map<String, Object>>) readinessData.get("rankedProperties");
            if (rankedList != null && !rankedList.isEmpty()) {
                answer += "\n\n**Property Expansion Ranking:**\n" + rankedList.stream()
                        .map(p -> "• **" + p.get("propertyName") + "** (" + p.get("city") + ") — Score: "
                                + p.get("readinessScore") + "/100 · " + p.get("recommendedNextAction"))
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
        } else if (((q.contains("list") || q.contains("listing") || q.contains("draft"))
                && (q.contains("property") || q.contains("bay") || q.contains("airport") || q.contains("faizabad")))
                || ((q.contains("faizabad") || q.contains("ayodhya"))
                && (q.contains("kw") || q.contains("bay")) && (q.contains("commercial") || q.contains("highway") || q.contains("retail")))) {
            String listingCity = q.contains("faizabad") ? "Faizabad" : (q.contains("ayodhya") ? "Ayodhya" : "");
            String listingTitle = listingCity.isBlank() ? "New Host EV Site" : listingCity + " Airport EV Hub";
            String listingAddress = extractListingAddress(rawQuestion);
            Integer listingBays = extractInteger(rawQuestion, "(?i)(\\d+)\\s*[- ]?\\s*(?:parking\\s*)?bays?");
            Double listingLoad = extractDecimal(rawQuestion, "(?i)(\\d+(?:\\.\\d+)?)\\s*kW");
            PropertyType listingType = extractPropertyType(q);
            String listingHours = extractOperatingHours(rawQuestion);
            Map<String, Object> duplicateResult = checkPropertyDuplicate(accountId, listingTitle, listingAddress, listingCity);
            if (Boolean.TRUE.equals(duplicateResult.get("duplicate"))) {
                answer = String.valueOf(duplicateResult.get("message"));
            } else {
                List<String> missing = new ArrayList<>();
                if (listingAddress.isBlank()) missing.add("approximate address or road");
                if (listingCity.isBlank()) missing.add("city");
                if (listingBays == null) missing.add("parking capacity");
                if (listingLoad == null) missing.add("available electrical load in kW");
                if (listingType == null) missing.add("property type");
                if (listingHours.isBlank()) missing.add("operating hours");
                if (!missing.isEmpty()) {
                    answer = "I can prepare this property listing, but I still need: " + String.join(", ", missing)
                            + ". I will check the completed draft for duplicates and ask before creating it.";
                } else {
                    answer = "I have prepared the draft listing for **" + listingTitle + "** (" + listingBays
                            + " parking bays, " + listingLoad + " kW, " + listingType + ", " + listingHours
                            + "). Please review and approve the action below to create it as a non-public draft.";
                actions.add(linkedMap(
                        "action", "CREATE_PROPERTY_DRAFT",
                        "label", "Create property draft",
                        "requiresConfirmation", true,
                        "detail", "Save the validated LandListing as a non-public draft. Verification and publishing remain separate.",
                        "payload", linkedMap(
                                "title", listingTitle,
                                "address", listingAddress,
                                "city", listingCity,
                                "state", "Uttar Pradesh",
                                "availableParkingBays", listingBays,
                                "availableLoadKw", listingLoad,
                                "propertyType", listingType,
                                "operatingHours", listingHours,
                                "powerPhase", "NOT_SURE"
                        )
                ));
                }
            }
        } else if (q.contains("offer") || q.contains("proposal") || q.contains("cpo") || q.contains("operator deal")
                || q.contains("deal") || q.contains("compare company")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> compSum = (Map<String, Object>) offersData.get("comparisonSummary");
            answer = "### Operator Offer Comparison for " + offersData.get("propertyName") + " (" + offersData.get("city") + ")\n\n"
                    + "• **Best Financial Upside:** " + compSum.get("bestFinancialOption") + "\n"
                    + "• **Lowest Host Capex:** " + compSum.get("lowestHostCapex") + "\n"
                    + "• **Shortest Lock-in:** " + compSum.get("shortestCommitment") + "\n"
                    + "• **Best Hardware:** " + compSum.get("bestInfrastructure") + "\n\n"
                    + "*Note: " + compSum.get("dataNotice") + "*";
        } else if (q.contains("which charger") || q.contains("servicing") || q.contains("needs service")
                || q.contains("maintenance") || q.contains("service") || q.contains("repair")
                || q.contains("fault") || q.contains("health") || q.contains("issue") || q.contains("attention")
                || q.contains("problem") || q.contains("wrong")) {
            answer = String.valueOf(chargerHealthData.get("summary"));
            if (chargerHealthData.get("preparedAction") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pAction = (Map<String, Object>) chargerHealthData.get("preparedAction");
                actions.add(pAction);
            }
        } else if (q.contains("which car") || q.contains("cars charging") || q.contains("vehicles charging")
                || q.contains("occupied")) {
            answer = liveSessions.isEmpty()
                    ? "No vehicle is charging at Prince's stations right now. All occupancy comes from live session state, not an AI guess."
                    : liveSessions.size() + " vehicle(s) are charging now: " + liveSessions.stream()
                            .map(item -> item.get("vehicleName") + " on " + item.get("chargerCode") + " at "
                                    + item.get("stationName") + " · " + item.get("currentBatteryPercent") + "% → "
                                    + item.get("targetBatteryPercent") + "% · about " + item.get("remainingMinutes")
                                    + " min remaining")
                            .collect(java.util.stream.Collectors.joining("; ")) + ".";
        } else if (q.contains("solar") || q.contains("subsid") || q.contains("government") || q.contains("finance")) {
            answer = solar.get("stationName") + " could model " + solar.get("solarContributionPercent")
                    + "% solar contribution and about ₹" + solar.get("monthlySavings")
                    + " monthly grid-cost reduction. The modeled TATA/CPO + assistance + RESCO structure stays within Prince's ₹10 lakh budget. Incentive leads require current eligibility verification; Vidyut will not claim or submit anything without approval.";
        } else if (q.contains("peak") || q.contains("demand") || q.contains("availability")
                || q.contains("traffic") || q.contains("open") || q.contains("stay") || q.contains("time")) {
            answer = "Recorded demand peaks at " + operatingHours.get("peakWindow") + ". "
                    + operatingHours.get("recommendation") + " I will ask before changing published hours.";
        } else if (q.contains("earning") || q.contains("revenue") || q.contains("increase") || q.contains("forecast")) {
            answer = "Today's recorded gross revenue is ₹" + earnings.get("todayGrossRevenue")
                    + "; modeled electricity cost is ₹" + earnings.get("todayElectricityCost")
                    + " and platform share is ₹" + earnings.get("todayPlatformShare")
                    + ", leaving estimated host earnings of ₹" + earnings.get("todayHostNet") + ".";
        } else {
            answer = "Prince, you have " + dashboard.get("totalChargers") + " connectors across " + stations.size()
                    + " corridor hubs, " + dashboard.get("upcomingBookings") + " upcoming bookings and "
                    + dashboard.get("uptimePercent") + "% uptime. The highest service risk and best opening-hours opportunity are shown below.";
        }
        List<Map<String, Object>> portfolio = stations.stream().<Map<String, Object>>map(station -> linkedMap(
                "stationId", station.getId(), "stationName", station.getName(),
                "propertyOwnerName", Objects.toString(station.getPropertyOwnerName(), "Prince"),
                "operatorCompanyName", Objects.toString(station.getOperatorCompanyName(), "Host operated"),
                "operatingModel", Objects.toString(station.getOperatingModel(), "HOST_OPERATED"),
                "solarProviderName", station.getSolarProviderName(), "connectorCount", station.getConnectors().size(),
                "onlineConnectors", station.getConnectors().stream()
                        .filter(connector -> connector.getStatus() == ChargerStatus.ONLINE).count(),
                "networkHealth", station.getConnectors().stream().anyMatch(connector -> connector.getHealthScore() < 60)
                        ? "ATTENTION" : "HEALTHY", "demoData", station.isDemoData())).toList();
        Map<String, Object> result = linkedMap("question", rawQuestion, "answer", answer,
                "revenue", earnings, "maintenanceRisks", maintenance,
                "operatingHours", operatingHours, "companyDeals", companyDeals,
                "solarOpportunity", solar, "networkPortfolio", portfolio,
                "liveSessions", liveSessions,
                "propertyReadiness", readinessData,
                "companyOffers", offersData,
                "hostedChargerHealth", chargerHealthData,
                "outagePlaybook", linkedMap("steps", List.of(
                                "Stop new reservations on the failed connector",
                                "Protect or reroute affected journeys according to their autonomy mode",
                                "Notify the equipment operator and prepare a maintenance ticket",
                                "Compare repair expense with downtime and replacement economics",
                                "Offer a customer credit only after Host approval"),
                        "approvalPolicy", "No credit, company contact, contract, payment or scheme submission is automatic"),
                "proposedActions", actions,
                "generatedAt", LocalDateTime.now(), "dataPolicy",
                "Financial figures are calculated from stored bookings or explicitly labeled demo assumptions; legal, subsidy, and contract actions are never automatic.");
        if (authorization != null && !authorization.isBlank() && roleScopedAgentService != null) {
            RoleScopedAgentService.GroundedReply grounded = roleScopedAgentService.explain(
                    authorization, "HOST", accountId, rawQuestion, answer, result);
            result.put("answer", grounded.answer());
            result.put("assistantModel", grounded.model());
            result.put("assistantProvider", grounded.provider());
            result.put("assistantFallback", grounded.deterministicFallback());
        } else {
            result.put("assistantModel", "deterministic-host-fallback");
            result.put("assistantProvider", "DETERMINISTIC");
            result.put("assistantFallback", true);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> executeAgentAction(Long accountId, HostAgentActionRequest request) {
        requireOperationalHost(accountId);
        if (!request.isApproved()) {
            throw new BadRequestException("Host approval is required before Vidyut executes this action");
        }
        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "REQUEST_TATA_SERVICE", "REQUEST_SERVICE" -> {
                if (request.getConnectorId() == null) throw new BadRequestException("Choose a connector first");
                ChargingConnector connector = ownedConnector(accountId, request.getConnectorId());
                ChargingStation station = connector.getStation();
                HostProfile profile = requireHost(accountId);
                String operatorName = station.getOperatorCompanyName() != null ? station.getOperatorCompanyName() : "Tata Power — Demo Operator Data";
                notificationService.sendNotification(accountId, "Service request submitted",
                        "Urgent service request sent to " + operatorName + " for connector " + connector.getChargerCode(),
                        NotificationType.SYSTEM_ALERT);
                if (station.getOperatorCompanyId() != null) {
                    companyRepository.findById(station.getOperatorCompanyId()).ifPresent(company -> {
                        if (company.getAccount() != null) {
                            notificationService.sendNotification(
                                    company.getAccount().getId(),
                                    "Urgent service requested by Host",
                                    "Host " + profile.getDisplayName() + " requested maintenance service for " + connector.getChargerCode() + " at " + station.getName(),
                                    NotificationType.FAULT_ALERT
                            );
                        }
                    });
                    maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(station.getOperatorCompanyId()).stream()
                            .filter(t -> t.getChargerId().equals(connector.getId()) && t.getStatus() == com.vidyut.company.entity.MaintenanceTicketStatus.OPEN)
                            .findFirst().ifPresent(ticket -> {
                                ticket.setPriority(com.vidyut.company.entity.MaintenancePriority.CRITICAL);
                                ticket.setIssue("HOST SERVICE REQUEST: " + profile.getDisplayName() + " requested urgent service for " + connector.getChargerCode() + ". " + ticket.getIssue());
                                maintenanceTicketRepository.save(ticket);
                            });
                }
                yield linkedMap("status", "EXECUTED", "action", action,
                        "connectorCode", connector.getChargerCode(),
                        "operator", operatorName,
                        "serviceStatus", "SERVICE_REQUESTED",
                        "message", "Service request dispatched to " + operatorName + ". Awaiting technician dispatch.");
            }
            case "PUT_CONNECTOR_IN_MAINTENANCE" -> {
                if (request.getConnectorId() == null) throw new BadRequestException("Choose a connector first");
                ChargingConnector connector = ownedConnector(accountId, request.getConnectorId());
                HostChargerStatusRequest status = new HostChargerStatusRequest();
                status.setStatus(ChargerStatus.MAINTENANCE);
                status.setHealthScore(connector.getHealthScore());
                status.setSessionEnergyKwh(connector.getSessionEnergyKwh());
                status.setFaultCode("HOST_AGENT_APPROVED_SERVICE");
                status.setImpactApproved(true);
                yield linkedMap("status", "EXECUTED", "action", action,
                        "result", updateChargerStatus(accountId, connector.getId(), status));
            }
            case "REOPEN_CONNECTOR" -> {
                if (request.getConnectorId() == null) throw new BadRequestException("Choose a connector first");
                ChargingConnector connector = ownedConnector(accountId, request.getConnectorId());
                HostChargerStatusRequest status = new HostChargerStatusRequest();
                status.setStatus(ChargerStatus.ONLINE);
                status.setHealthScore(Math.max(85, connector.getHealthScore()));
                status.setSessionEnergyKwh(connector.getSessionEnergyKwh());
                status.setImpactApproved(true);
                yield linkedMap("status", "EXECUTED", "action", action,
                        "result", updateChargerStatus(accountId, connector.getId(), status));
            }
            case "EXTEND_HOURS" -> {
                if (request.getStationId() == null) throw new BadRequestException("Choose a station first");
                ChargingStation station = ownedStation(accountId, request.getStationId());
                station.setWorkingHours("06:00-24:00");
                station.setWeeklySchedule("MON-SUN 06:00-24:00");
                station.setAutoAvailability(true);
                stationRepository.save(station);
                notificationService.sendNotification(accountId, "Operating hours extended",
                        station.getName() + " is discoverable and bookable until midnight.",
                        NotificationType.SYSTEM_ALERT);
                yield linkedMap("status", "EXECUTED", "action", action, "stationId", station.getId(),
                        "message", "Published hours updated to 06:00–24:00.");
            }
            case "PREPARE_GREEN_FINANCE" -> linkedMap("status", "PREPARED_NOT_SUBMITTED", "action", action,
                    "stationId", request.getStationId(), "documents", List.of(
                            "Recent electricity bill", "Property ownership or authorization",
                            "Site/roof area evidence", "Commercial connection details", "Bank and tax details"),
                    "workstreams", List.of("Current state EV-policy eligibility check",
                            "Nodal-agency/CPO route check", "TATA/ChargeZone/Statiq operator comparison",
                            "Purchase vs finance vs RESCO solar comparison"),
                    "modeledFundingStructure", linkedMap("projectCost", 3_500_000,
                            "princeContribution", 1_000_000, "operatorContribution", 1_750_000,
                            "potentialAssistance", 700_000, "solarProviderContribution", 50_000,
                            "solarStructure", "RESCO_PPA",
                            "additionalUpfrontRequired", 0),
                    "message", "The funding checklist and modeled split are prepared, not submitted. Scheme eligibility, company terms and every external commitment still require Prince's explicit approval.");
            case "CREATE_PROPERTY_DRAFT" -> {
                Map<String, Object> p = request.getPayload() != null ? request.getPayload() : Map.of();
                String title = requiredPayloadText(p, "title", "Property title is required");
                String address = requiredPayloadText(p, "address", "Approximate property address is required");
                String city = requiredPayloadText(p, "city", "Property city is required");
                String state = Objects.toString(p.get("state"), "Uttar Pradesh");
                int bays = requiredPayloadNumber(p, "availableParkingBays", "Parking capacity is required").intValue();
                double load = requiredPayloadNumber(p, "availableLoadKw", "Available electrical load is required").doubleValue();
                String typeStr = requiredPayloadText(p, "propertyType", "Property type is required").toUpperCase(Locale.ROOT).replace(' ', '_');
                String operatingHours = requiredPayloadText(p, "operatingHours", "Operating hours are required");
                if (bays < 1 || bays > 1000) throw new BadRequestException("Parking capacity must be between 1 and 1000 bays");
                if (load <= 0) throw new BadRequestException("Available electrical load must be greater than 0 kW");
                PropertyType propType;
                try { propType = PropertyType.valueOf(typeStr); }
                catch (Exception e) { throw new BadRequestException("Choose a supported property type"); }
                PowerPhase powerPhase;
                try {
                    powerPhase = PowerPhase.valueOf(Objects.toString(p.get("powerPhase"), "NOT_SURE")
                            .toUpperCase(Locale.ROOT).replace(' ', '_'));
                } catch (Exception e) {
                    powerPhase = PowerPhase.NOT_SURE;
                }

                LandListingCreateRequest createReq = LandListingCreateRequest.builder()
                        .title(title)
                        .address(address)
                        .city(city)
                        .state(state)
                        .availableParkingBays(bays)
                        .availableLoadKw(load)
                        .propertyType(propType)
                        .powerPhase(powerPhase)
                        .operatingHours(operatingHours)
                        .discoverable(false)
                        .build();

                Map<String, Object> res = createPropertyDraft(accountId, createReq);
                yield linkedMap("status", "EXECUTED", "action", action,
                        "result", res,
                        "message", res.get("message"));
            }
            case "SUBMIT_PROPERTY_FOR_VERIFICATION" -> {
                LandListing property = ownedProperty(accountId, request.getPropertyId());
                if (property.getOwnershipDocumentUrl() == null || property.getOwnershipDocumentUrl().isBlank()
                        || property.getElectricityDocumentUrl() == null || property.getElectricityDocumentUrl().isBlank()) {
                    yield linkedMap("status", "VALIDATION_REQUIRED", "action", action,
                            "propertyId", property.getId(),
                            "missingFields", List.of("ownershipDocumentUrl", "electricityDocumentUrl"),
                            "message", "Upload ownership and electricity documents before submitting this draft for verification.");
                }
                property.setStatus(LandListingStatus.PENDING_APPROVAL);
                property.setVerificationStage("SUBMITTED");
                property.setDiscoverable(false);
                landListingRepository.save(property);
                yield linkedMap("status", "SUBMITTED", "action", action, "propertyId", property.getId(),
                        "listingStatus", property.getStatus(), "verificationStage", property.getVerificationStage(),
                        "message", "Property #" + property.getId() + " submitted for verification. It is not yet public.");
            }
            case "PUBLISH_PROPERTY" -> {
                LandListing property = ownedProperty(accountId, request.getPropertyId());
                if (property.getStatus() != LandListingStatus.APPROVED && property.getStatus() != LandListingStatus.ACTIVE) {
                    yield linkedMap("status", "VALIDATION_REQUIRED", "action", action,
                            "propertyId", property.getId(), "listingStatus", property.getStatus(),
                            "message", "This property must pass verification before it can be published.");
                }
                property.setStatus(LandListingStatus.ACTIVE);
                property.setVerificationStage("PUBLISHED");
                property.setDiscoverable(true);
                landListingRepository.save(property);
                yield linkedMap("status", "PUBLISHED", "action", action, "propertyId", property.getId(),
                        "listingStatus", property.getStatus(), "discoverable", property.isDiscoverable(),
                        "message", "Property #" + property.getId() + " is now published in the marketplace.");
            }
            default -> throw new BadRequestException("This Host Agent action is not executable");
        };
    }

    public Map<String, Object> evaluatePropertyReadiness(Long accountId) {
        requireHost(accountId);
        List<LandListing> properties = landListingRepository.findByHostUserId(accountId);
        if (properties.isEmpty()) {
            return linkedMap(
                    "rankedProperties", List.of(),
                    "topRecommendation", "No properties found in your Host portfolio. List a property first to evaluate expansion readiness.",
                    "totalPropertiesEvaluated", 0
            );
        }

        List<Map<String, Object>> ranked = new ArrayList<>();
        for (LandListing property : properties) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            List<String> constraints = new ArrayList<>();

            int bays = property.getAvailableParkingBays();
            if (bays >= 6) {
                score += 25;
                reasons.add("Large parking footprint: " + bays + " bays available for dedicated EV charging");
            } else if (bays >= 4) {
                score += 20;
                reasons.add("Adequate parking capacity: " + bays + " dedicated charging bays");
            } else {
                score += 10;
                constraints.add("Limited to " + bays + " bay(s); may constrain multi-vehicle throughput");
            }

            double loadKw = property.getAvailableLoadKw();
            if (loadKw >= 200.0) {
                score += 30;
                reasons.add("Heavy electrical capacity: " + loadKw + " kW load ready for multi-gun DC fast charging (120–180 kW)");
            } else if (loadKw >= 100.0) {
                score += 24;
                reasons.add("Good power capacity: " + loadKw + " kW suitable for high-speed DC charging");
            } else if (loadKw >= 50.0) {
                score += 15;
                reasons.add("Moderate power capacity: " + loadKw + " kW suitable for 30–60 kW fast charger or multi-AC setup");
            } else {
                score += 8;
                constraints.add("Low electrical load (" + loadKw + " kW); transformer or meter upgrade required for DC fast charging");
            }

            if (property.getPowerPhase() == PowerPhase.THREE_PHASE) {
                score += 15;
                reasons.add("Industrial 3-phase grid supply connected; supports standard commercial DC chargers");
            } else {
                score += 5;
                constraints.add("Power phase is " + property.getPowerPhase() + "; 3-phase conversion needed for DC fast charging");
            }

            if (property.getPropertyType() == PropertyType.HIGHWAY) {
                score += 15;
                reasons.add("Highway transit location (" + property.getPropertyType() + ") captures long-distance intercity charging demand");
            } else if (property.getPropertyType() == PropertyType.COMMERCIAL_PARKING) {
                score += 12;
                reasons.add("Commercial parking facility (" + property.getPropertyType() + ") provides strong daytime dwell time");
            } else {
                score += 8;
                reasons.add("Property type: " + property.getPropertyType());
            }

            String hours = property.getOperatingHours();
            if (hours != null && (hours.toLowerCase(Locale.ROOT).contains("24") || hours.toLowerCase(Locale.ROOT).contains("open"))) {
                score += 10;
                reasons.add("24/7 operating availability ensures round-the-clock revenue generation");
            } else {
                score += 5;
                constraints.add("Restricted operating hours (" + (hours != null ? hours : "Unspecified") + ") limits night charging revenue");
            }

            if (property.getStatus() == LandListingStatus.APPROVED || property.getStatus() == LandListingStatus.ACTIVE) {
                score += 5;
                reasons.add("Ownership and site documents fully verified by Vidyut");
            } else {
                constraints.add("Listing status is " + property.getStatus() + "; pending final verification");
            }

            int finalScore = Math.min(100, Math.max(score, property.getPropertyScore() != null ? property.getPropertyScore() : score));

            String nextAction = finalScore >= 85
                    ? "Ready for CPO partnership: Deploy 2x 120-150 kW dual-gun CCS2 fast chargers"
                    : finalScore >= 65
                    ? "Upgrade electrical load to 100+ kW and apply for CPO revenue-share proposals"
                    : "Complete verification and submit transformer upgrade application";

            ranked.add(linkedMap(
                    "propertyId", property.getId(),
                    "propertyName", property.getTitle(),
                    "city", Objects.toString(property.getCity(), "N/A"),
                    "propertyType", property.getPropertyType().toString(),
                    "availableParkingBays", property.getAvailableParkingBays(),
                    "availableLoadKw", property.getAvailableLoadKw(),
                    "powerPhase", property.getPowerPhase().toString(),
                    "readinessScore", finalScore,
                    "reasons", reasons,
                    "constraints", constraints,
                    "recommendedNextAction", nextAction
            ));
        }

        ranked.sort((a, b) -> Integer.compare((int) b.get("readinessScore"), (int) a.get("readinessScore")));

        Map<String, Object> top = ranked.get(0);
        String recommendation = top.get("propertyName") + " (" + top.get("city") + ") ranks highest with an expansion readiness score of "
                + top.get("readinessScore") + "/100 due to its " + top.get("availableLoadKw") + " kW electrical capacity, "
                + top.get("availableParkingBays") + " bays, and 3-phase grid readiness.";

        return linkedMap(
                "rankedProperties", ranked,
                "topRecommendation", recommendation,
                "totalPropertiesEvaluated", ranked.size()
        );
    }

    public Map<String, Object> compareCompanyOffers(Long accountId, String propertyFilter) {
        requireHost(accountId);
        List<LandListing> properties = landListingRepository.findByHostUserId(accountId);
        LandListing target = null;
        if (propertyFilter != null && !propertyFilter.isBlank()) {
            String filter = propertyFilter.toLowerCase(Locale.ROOT).trim();
            target = properties.stream().filter(p ->
                    p.getTitle().toLowerCase(Locale.ROOT).contains(filter)
                    || (p.getCity() != null && p.getCity().toLowerCase(Locale.ROOT).contains(filter))
                    || String.valueOf(p.getId()).equals(filter)
            ).findFirst().orElse(null);
        }
        if (target == null && !properties.isEmpty()) {
            target = properties.stream().filter(p -> "Agra".equalsIgnoreCase(p.getCity())).findFirst()
                    .orElse(properties.get(0));
        }

        String propName = target != null ? target.getTitle() : "Agra Highway Expressway Hub";
        String city = target != null && target.getCity() != null ? target.getCity() : "Agra";

        List<Map<String, Object>> offers = List.of(
                linkedMap(
                        "operatorName", "Vidyut Demo Operator Alpha",
                        "operatorType", "SYNTHETIC DEMO CPO — NO COMMERCIAL AFFILIATION",
                        "commercialModel", "REVENUE_SHARE",
                        "hostRevenueSharePercent", 70.0,
                        "monthlyLeasePayout", 0.0,
                        "hostCapexRequirement", 0.0,
                        "operatorCapexRequirement", 1_850_000.0,
                        "proposedHardware", "2x 120 kW Dual CCS2 DC Fast Chargers",
                        "contractDurationYears", 5,
                        "maintenanceResponsibility", "OPERATOR_FULL",
                        "summary", "70% revenue share to Host with zero upfront capital expenditure. Highest long-term earning potential on high-traffic corridors.",
                        "tradeoff", "Higher revenue upside; payouts fluctuate with EV charging utilization.",
                        "recommendationTag", "BEST_FINANCIAL_UPSIDE"
                ),
                linkedMap(
                        "operatorName", "GreenRoute Charging Demo",
                        "operatorType", "SYNTHETIC DEMO CPO — NO COMMERCIAL AFFILIATION",
                        "commercialModel", "FIXED_LEASE",
                        "hostRevenueSharePercent", 0.0,
                        "monthlyLeasePayout", 45_000.0,
                        "hostCapexRequirement", 0.0,
                        "operatorCapexRequirement", 2_400_000.0,
                        "proposedHardware", "2x 150 kW Ultra-fast CCS2 Chargers",
                        "contractDurationYears", 3,
                        "maintenanceResponsibility", "OPERATOR_FULL",
                        "summary", "₹45,000/month guaranteed lease with ₹0 Host capex and 3-year term. Best low-risk option with zero utilization risk.",
                        "tradeoff", "Guaranteed steady cashflow; Host does not participate in surge charging volume upside.",
                        "recommendationTag", "BEST_LOW_RISK_OFFER"
                ),
                linkedMap(
                        "operatorName", "VoltGrid Demo CPO",
                        "operatorType", "SYNTHETIC DEMO CPO — NO COMMERCIAL AFFILIATION",
                        "commercialModel", "HYBRID_CO_INVESTMENT",
                        "hostRevenueSharePercent", 20.0,
                        "monthlyLeasePayout", 20_000.0,
                        "hostCapexRequirement", 450_000.0,
                        "operatorCapexRequirement", 1_350_000.0,
                        "proposedHardware", "4x 60 kW Fast Chargers",
                        "contractDurationYears", 7,
                        "maintenanceResponsibility", "SHARED_HOST_OPERATOR",
                        "summary", "Base ₹20,000/mo guaranteed rent plus 20% revenue share. Requires 25% Host co-investment.",
                        "tradeoff", "Balanced downside floor with upside participation, but requires ₹4.5 lakh upfront Host capital.",
                        "recommendationTag", "BALANCED_CO_INVESTMENT"
                )
        );

        return linkedMap(
                "propertyId", target != null ? target.getId() : 1L,
                "propertyName", propName,
                "city", city,
                "offers", offers,
                "comparisonSummary", linkedMap(
                        "bestFinancialOption", "Vidyut Demo Operator Alpha (70% revenue share captures highest monthly upside)",
                        "lowestHostCapex", "Vidyut Demo Operator Alpha & GreenRoute Charging Demo (₹0 upfront Host capex)",
                        "shortestCommitment", "GreenRoute Charging Demo (3-year contract lock-in)",
                        "bestInfrastructure", "GreenRoute Charging Demo (2x 150 kW ultra-fast chargers)",
                        "dataNotice", "All operator proposals are SYNTHETIC DEMO DATA for platform demonstration. No real commercial affiliation."
                )
        );
    }

    public Map<String, Object> getHostedChargerHealth(Long accountId) {
        requireHost(accountId);
        List<ChargingConnector> connectors = connectorRepository.findByStation_HostUserId(accountId);

        List<Map<String, Object>> hostedChargers = new ArrayList<>();
        List<Map<String, Object>> attentionRequired = new ArrayList<>();

        for (ChargingConnector connector : connectors) {
            ChargingStation st = connector.getStation();
            boolean isFaulted = connector.getStatus() == ChargerStatus.MAINTENANCE
                    || connector.getStatus() == ChargerStatus.FAULT
                    || connector.getStatus() == ChargerStatus.SUSPECTED_FAULT
                    || connector.getStatus() == ChargerStatus.OFFLINE
                    || connector.getHealthScore() < 60
                    || (connector.getFaultCode() != null && !connector.getFaultCode().isBlank() && !"NONE".equalsIgnoreCase(connector.getFaultCode()));

            Map<String, Object> cInfo = linkedMap(
                    "connectorId", connector.getId(),
                    "chargerCode", connector.getChargerCode(),
                    "stationId", st.getId(),
                    "stationName", st.getName(),
                    "city", st.getCity(),
                    "status", connector.getStatus().toString(),
                    "healthScore", connector.getHealthScore(),
                    "powerKw", connector.getPowerKw(),
                    "connectorType", connector.getType() != null ? connector.getType().name() : "CCS2",
                    "faultCode", connector.getFaultCode() != null ? connector.getFaultCode() : "NONE",
                    "operatorCompany", Objects.toString(st.getOperatorCompanyName(), "Operator not recorded"),
                    "ownershipType", st.getOwnershipType(), "propertyId", st.getHostPartnershipId(),
                    "faultReason", connector.getFaultReason(), "source", connector.getStatusSource(),
                    "canControlOperationalStatus", st.getOperatorCompanyId() == null && st.getSupplierCompanyId() == null
            );
            hostedChargers.add(cInfo);

            if (isFaulted) {
                attentionRequired.add(cInfo);
            }
        }

        Map<String, Object> preparedAction = null;
        if (!attentionRequired.isEmpty()) {
            Map<String, Object> worst = attentionRequired.get(0);
            preparedAction = linkedMap(
                    "action", "REQUEST_SERVICE",
                    "label", "Request urgent service for " + worst.get("chargerCode"),
                    "connectorId", worst.get("connectorId"),
                    "stationId", worst.get("stationId"),
                    "chargerCode", worst.get("chargerCode"),
                    "stationName", worst.get("stationName"),
                    "operator", worst.get("operatorCompany"),
                    "requiresConfirmation", true,
                    "detail", "Dispatches high-priority maintenance ticket to " + worst.get("operatorCompany") + " for " + worst.get("chargerCode")
            );
        }

        String summary = attentionRequired.isEmpty()
                ? "All " + connectors.size() + " chargers across your hosted properties have no recorded operational alerts."
                : attentionRequired.size() + " hosted charger(s) require attention. Specifically, "
                + attentionRequired.get(0).get("chargerCode") + " at " + attentionRequired.get(0).get("stationName")
                + " reports status " + attentionRequired.get(0).get("status") + " (Health: " + attentionRequired.get(0).get("healthScore") + "%).";

        return linkedMap(
                "totalHostedChargers", connectors.size(),
                "healthyChargersCount", connectors.size() - attentionRequired.size(),
                "attentionRequiredCount", attentionRequired.size(),
                "summary", summary,
                "attentionRequiredList", attentionRequired,
                "allHostedChargers", hostedChargers,
                "preparedAction", preparedAction
        );
    }

    @Transactional
    public Map<String, Object> createPropertyDraft(Long accountId, LandListingCreateRequest request) {
        operationalControlService.assertHostCanCreateListing(accountId);

        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        String city = request.getCity() != null ? request.getCity().trim() : "";
        String address = request.getAddress() != null ? request.getAddress().trim() : "";

        List<LandListing> existing = landListingRepository.findByHostUserId(accountId);
        for (LandListing p : existing) {
            boolean titleMatch = p.getTitle().equalsIgnoreCase(title);
            boolean cityAndAddrMatch = p.getCity() != null && p.getCity().equalsIgnoreCase(city)
                    && p.getAddress() != null && (
                            p.getAddress().equalsIgnoreCase(address)
                            || (address.length() > 5 && p.getAddress().toLowerCase(Locale.ROOT).contains(address.toLowerCase(Locale.ROOT)))
                    );
            if (titleMatch || cityAndAddrMatch) {
                return linkedMap(
                        "status", "DUPLICATE_FOUND",
                        "existingPropertyId", p.getId(),
                        "existingTitle", p.getTitle(),
                        "existingCity", p.getCity(),
                        "message", "I found an existing property matching this location: \"" + p.getTitle()
                                + "\" in " + p.getCity() + ". Would you like to update it instead of creating a duplicate?"
                );
            }
        }

        LandListingResponse created = landListingService.createListing(accountId, request);
        LandListing draft = landListingRepository.findByIdAndHostUserId(created.getId(), accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Created property draft could not be loaded"));
        draft.setVerificationStage("DRAFT");
        draft.setDiscoverable(false);
        draft.setStatus(LandListingStatus.PENDING_APPROVAL);
        landListingRepository.save(draft);

        return linkedMap(
                "status", "CREATED",
                "propertyId", created.getId(),
                "title", created.getTitle(),
                "city", created.getCity(),
                "availableParkingBays", created.getAvailableParkingBays(),
                "availableLoadKw", created.getAvailableLoadKw(),
                "listingStatus", created.getStatus().toString(),
                "verificationStage", "DRAFT",
                "discoverable", false,
                "message", "Property draft \"" + created.getTitle() + "\" created successfully with ID #"
                        + created.getId() + ". It is saved as a non-public DRAFT."
        );
    }

    public Map<String, Object> checkPropertyDuplicate(Long accountId, String title, String address, String city) {
        requireHost(accountId);
        Optional<LandListing> duplicate = findPropertyDuplicate(accountId, title, address, city);
        if (duplicate.isEmpty()) {
            return linkedMap("status", "NO_DUPLICATE", "duplicate", false,
                    "message", "No matching property was found in this Host portfolio.");
        }
        LandListing property = duplicate.get();
        return linkedMap("status", "DUPLICATE_FOUND", "duplicate", true,
                "existingPropertyId", property.getId(), "existingTitle", property.getTitle(),
                "existingAddress", property.getAddress(), "existingCity", property.getCity(),
                "message", "An existing property matches this listing. Update property #" + property.getId() + " instead.");
    }

    public Map<String, Object> preparePropertyListing(Long accountId, LandListingCreateRequest request) {
        requireHost(accountId);
        String title = Objects.toString(request.getTitle(), "").trim();
        String address = Objects.toString(request.getAddress(), "").trim();
        String city = Objects.toString(request.getCity(), "").trim();
        Optional<LandListing> duplicate = findPropertyDuplicate(accountId, title, address, city);
        if (duplicate.isPresent()) {
            LandListing property = duplicate.get();
            return linkedMap("status", "DUPLICATE_FOUND", "requiresConfirmation", false,
                    "existingPropertyId", property.getId(), "existingTitle", property.getTitle(),
                    "existingCity", property.getCity(),
                    "message", "A similar property already exists. Offer to update property #" + property.getId() + " instead.");
        }
        if (request.getAvailableParkingBays() == null || request.getAvailableParkingBays() < 1
                || request.getAvailableLoadKw() == null || request.getAvailableLoadKw() <= 0
                || request.getPropertyType() == null || request.getPropertyType() == PropertyType.OTHER
                || request.getOperatingHours() == null || request.getOperatingHours().isBlank()) {
            throw new BadRequestException("Parking bays, positive electrical load, property type, and operating hours are required");
        }
        Map<String, Object> payload = linkedMap(
                "title", title, "address", address, "city", city,
                "state", Objects.toString(request.getState(), ""),
                "availableParkingBays", request.getAvailableParkingBays(),
                "availableLoadKw", request.getAvailableLoadKw(),
                "propertyType", request.getPropertyType(),
                "operatingHours", request.getOperatingHours());
        return linkedMap("status", "READY_FOR_APPROVAL", "requiresConfirmation", true,
                "action", "CREATE_PROPERTY_DRAFT", "draft", payload,
                "message", "Draft prepared. Ask the Host to approve creation; this does not publish the property.");
    }

    private Optional<LandListing> findPropertyDuplicate(Long accountId, String title, String address, String city) {
        String normalizedTitle = normalizedListingText(title);
        String normalizedAddress = normalizedListingText(address);
        String normalizedCity = normalizedListingText(city);
        return landListingRepository.findByHostUserId(accountId).stream().filter(property -> {
            String propertyTitle = normalizedListingText(property.getTitle());
            String propertyAddress = normalizedListingText(property.getAddress());
            String propertyCity = normalizedListingText(property.getCity());
            boolean sameTitle = !normalizedTitle.isBlank()
                    && (propertyTitle.equals(normalizedTitle) || propertyTitle.contains(normalizedTitle) || normalizedTitle.contains(propertyTitle));
            boolean sameLocation = !normalizedCity.isBlank() && propertyCity.equals(normalizedCity)
                    && !normalizedAddress.isBlank()
                    && (propertyAddress.equals(normalizedAddress) || propertyAddress.contains(normalizedAddress) || normalizedAddress.contains(propertyAddress));
            boolean similarLocation = !normalizedCity.isBlank() && propertyCity.equals(normalizedCity)
                    && sharedListingTokens(propertyAddress, normalizedAddress) >= 2;
            return sameTitle || sameLocation || similarLocation;
        }).findFirst();
    }

    private String normalizedListingText(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private long sharedListingTokens(String first, String second) {
        Set<String> ignored = Set.of("near", "the", "at", "in", "gate", "road", "street", "plot");
        Set<String> left = new HashSet<>(Arrays.asList(first.split("\\s+")));
        Set<String> right = new HashSet<>(Arrays.asList(second.split("\\s+")));
        left.removeIf(token -> token.length() < 3 || ignored.contains(token));
        right.removeIf(token -> token.length() < 3 || ignored.contains(token));
        left.retainAll(right);
        return left.size();
    }

    private Integer extractInteger(String value, String pattern) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(Objects.toString(value, ""));
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private Double extractDecimal(String value, String pattern) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(Objects.toString(value, ""));
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    private PropertyType extractPropertyType(String normalizedQuestion) {
        if (normalizedQuestion.contains("commercial parking")) return PropertyType.COMMERCIAL_PARKING;
        if (normalizedQuestion.contains("fuel station") || normalizedQuestion.contains("petrol pump")) return PropertyType.FUEL_STATION;
        if (normalizedQuestion.contains("highway")) return PropertyType.HIGHWAY;
        if (normalizedQuestion.contains("residential")) return PropertyType.RESIDENTIAL;
        if (normalizedQuestion.contains("office")) return PropertyType.OFFICE;
        if (normalizedQuestion.contains("mall") || normalizedQuestion.contains("retail")) return PropertyType.MALL;
        if (normalizedQuestion.contains("hotel")) return PropertyType.HOTEL;
        return null;
    }

    private String extractListingAddress(String question) {
        String value = Objects.toString(question, "").trim();
        java.util.regex.Matcher explicit = java.util.regex.Pattern
                .compile("(?i)(?:address(?: is|:)?|at)\\s+([^,.;]+(?:[,][^.;]+)?)")
                .matcher(value);
        if (explicit.find()) return cleanExtractedAddress(explicit.group(1));
        java.util.regex.Matcher location = java.util.regex.Pattern
                .compile("(?i)([^,.;]*(?:road|street|highway|plot|terminal)[^,.;]*)")
                .matcher(value);
        return location.find() ? cleanExtractedAddress(location.group(1)) : "";
    }

    private String cleanExtractedAddress(String value) {
        return Objects.toString(value, "")
                .replaceFirst("(?i)\\s+with\\s+\\d+(?:\\.\\d+)?\\s*kW.*$", "")
                .replaceFirst("(?i)\\s+(?:and|which)\\s+(?:has|operates|is).*?$", "")
                .trim();
    }

    private String extractOperatingHours(String question) {
        String value = Objects.toString(question, "");
        if (value.toLowerCase(Locale.ROOT).contains("24/7")
                || value.toLowerCase(Locale.ROOT).contains("open 24 hours")) return "Open 24 hours";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\s*(?:-|to)\\s*\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)")
                .matcher(value);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private LandListing ownedProperty(Long accountId, Long propertyId) {
        if (propertyId == null) throw new BadRequestException("Choose a property first");
        return landListingRepository.findByIdAndHostUserId(propertyId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found for this Host account"));
    }

    private String requiredPayloadText(Map<String, Object> payload, String key, String message) {
        String value = Objects.toString(payload.get(key), "").trim();
        if (value.isBlank()) throw new BadRequestException(message);
        return value;
    }

    private Number requiredPayloadNumber(Map<String, Object> payload, String key, String message) {
        Object value = payload.get(key);
        if (!(value instanceof Number number)) throw new BadRequestException(message);
        return number;
    }

    private List<Map<String, Object>> maintenanceRisks(
            Long accountId,
            List<ChargingConnector> connectors,
            List<Booking> bookings
    ) {
        List<HostReview> reviews = reviewRepository.findByHostAccountIdOrderByCreatedAtDesc(accountId);
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return connectors.stream().<Map<String, Object>>map(connector -> {
            Long stationId = connector.getStation().getId();
            long sessions = bookings.stream().filter(booking -> booking.getStationId().equals(stationId)
                    && booking.getStatus() == BookingStatus.COMPLETED
                    && booking.getStartTime() != null && booking.getStartTime().isAfter(since)).count();
            long complaints = reviews.stream().filter(review -> review.getStationId().equals(stationId)
                    && review.getCreatedAt() != null && review.getCreatedAt().isAfter(since)
                    && (review.getRating() <= 3 || containsIssueLanguage(review.getComment()))).count();
            boolean hardwareSignal = connector.getFaultCode() != null && !connector.getFaultCode().isBlank();
            boolean slow = connector.getStatus() == ChargerStatus.CHARGING && connector.getCurrentPowerKw() > 0
                    && connector.getCurrentPowerKw() < connector.getPowerKw() * .75;
            int risk = Math.min(100, Math.max(0, 100 - connector.getHealthScore()
                    + (int) complaints * 6 + (hardwareSignal ? 15 : 0)
                    + (slow ? 15 : 0)
                    + (connector.getStatus() == ChargerStatus.FAULT ? 30 : 0)
                    + (connector.getStatus() == ChargerStatus.SUSPECTED_FAULT ? 40 : 0)));
            List<String> signals = new ArrayList<>();
            if (connector.getHealthScore() < 75) signals.add("hardware health " + connector.getHealthScore() + "/100");
            if (complaints > 0) signals.add(complaints + " customer complaint(s) in 7 days");
            if (hardwareSignal) signals.add(connector.getFaultCode());
            if (slow) signals.add("charging power below 75% of rating");
            if (signals.isEmpty()) signals.add("no elevated customer or hardware signal");
            boolean replacementCandidate = connector.getHealthScore() <= 55;
            double repairEstimate = replacementCandidate ? 42_000 : connector.getPowerKw() >= 100 ? 4_500 : 2_800;
            double revenueLoss24Hours = connector.getStation().isDemoData()
                    ? (connector.getPowerKw() >= 100 ? 6_700 : 3_400)
                    : round(Math.max(1, sessions) * 520);
            double monthlyContribution = connector.getStation().isDemoData()
                    ? (replacementCandidate ? 7_300 : 18_600) : round(Math.max(1, sessions) * 390);
            return linkedMap("connectorId", connector.getId(), "stationId", stationId,
                    "stationName", connector.getStation().getName(), "chargerCode", connector.getChargerCode(),
                    "operatorCompanyName", Objects.toString(connector.getStation().getOperatorCompanyName(), "Host operated"),
                "canControlOperationalStatus", connector.getStation().getOperatorCompanyId() == null && connector.getStation().getSupplierCompanyId() == null,
                    "connectorType", connector.getType(), "riskScore", risk,
                    "maintenanceHealth", 100 - risk, "recentSessions", sessions,
                    "customerComplaints", complaints, "signals", signals,
                    "repairEstimate", repairEstimate, "estimatedRevenueLoss24Hours", revenueLoss24Hours,
                    "monthlyContribution", monthlyContribution, "estimatedRepairHours", replacementCandidate ? 10 : 3,
                    "repeatedFailures90Days", replacementCandidate ? 5 : Math.max(1, complaints),
                    "assetAgeYears", replacementCandidate ? 6.8 : 2.4,
                    "financialRecommendation", replacementCandidate ? "COMPARE_REPLACEMENT" : "REPAIR_NOW",
                    "operatorAction", "Notify " + Objects.toString(connector.getStation().getOperatorCompanyName(), "maintenance provider"),
                    "recommendedWindow", "Before the next peak or during the lowest recorded demand hour");
        }).sorted(Comparator.comparingInt(item -> -((Number) item.get("riskScore")).intValue())).toList();
    }

    private Map<String, Object> operatingHoursRecommendation(
            List<ChargingStation> stations,
            List<Booking> bookings,
            Map<String, Object> earnings,
            int peak
    ) {
        double average = ((Number) earnings.get("averageSessionValue")).doubleValue();
        if (average <= 0) average = 130;
        long peakSessions = bookings.stream().filter(booking -> booking.getStartTime() != null
                && booking.getStartTime().getHour() >= peak
                && booking.getStartTime().getHour() <= Math.min(23, peak + 3)).count();
        long additionalSessions = Math.max(2, Math.min(10, Math.round(peakSessions * .35)));
        double extraRevenue = round(additionalSessions * average);
        return linkedMap("stationId", busiestStation(stations, bookings) == null ? null : busiestStation(stations, bookings).getId(),
                "peakWindow", String.format("%02d:00–%02d:00", peak, Math.min(24, peak + 3)),
                "recommendedHours", "06:00–24:00", "additionalSessionsLow", additionalSessions,
                "additionalSessionsHigh", Math.min(12, additionalSessions + 3),
                "estimatedAdditionalRevenue", round(extraRevenue),
                "estimatedAdditionalOperatingCost", round(extraRevenue * .34),
                "recommendation", "Keep the busiest corridor hub open until midnight.",
                "dataBasis", bookings.isEmpty() ? "DEMO_SCENARIO" : "RECORDED_BOOKING_HOURS");
    }

    private List<Map<String, Object>> companyDealScenarios(Map<String, Object> earnings) {
        List<Map<String, Object>> deals = new ArrayList<>();
        deals.add(linkedMap("company", "Vidyut Demo Operator Alpha (Demo CPO)", "chargerPowerKw", 120,
                "revenueModel", "REVENUE_SHARE", "hostShareLabel", "70%",
                "hostRevenueSharePercent", 70, "installationFunding", "COMPANY_FUNDED",
                "maintenanceResponsibility", "COMPANY", "expectedSessionsPerMonth", 1_250,
                "projectedMonthlyHostIncome", 48_300, "projectedAnnualHostRevenue", 579_600,
                "projectedThreeYearValue", 1_738_800, "riskLevel", "MEDIUM",
                "recommendationTag", "BEST_FINANCIAL_OFFER",
                "tradeoff", "Highest modeled upside; payouts fluctuate with EV charging volume. [SYNTHETIC DEMO DATA — NO AFFILIATION]", "demoScenario", true));
        deals.add(linkedMap("company", "GreenRoute Charging Demo (Demo CPO)", "chargerPowerKw", 150,
                "revenueModel", "FIXED_RENT", "hostShareLabel", "₹45,000/month",
                "hostRevenueSharePercent", 0, "installationFunding", "COMPANY_FUNDED",
                "maintenanceResponsibility", "COMPANY", "expectedSessionsPerMonth", 1_340,
                "projectedMonthlyHostIncome", 45_000, "projectedAnnualHostRevenue", 540_000,
                "projectedThreeYearValue", 1_620_000, "riskLevel", "LOW",
                "recommendationTag", "BEST_LOW_RISK_OFFER",
                "tradeoff", "Guaranteed steady lease payout with ₹0 Host capex. [SYNTHETIC DEMO DATA — NO AFFILIATION]", "demoScenario", true));
        deals.add(linkedMap("company", "VoltGrid Demo CPO (Demo CPO)", "chargerPowerKw", 60,
                "revenueModel", "HYBRID_CO_INVESTMENT", "hostShareLabel", "₹20,000 + 20%",
                "hostRevenueSharePercent", 20, "installationFunding", "SHARED",
                "maintenanceResponsibility", "SHARED", "expectedSessionsPerMonth", 900,
                "projectedMonthlyHostIncome", 32_000, "projectedAnnualHostRevenue", 384_000,
                "projectedThreeYearValue", 1_152_000, "riskLevel", "MEDIUM_LOW",
                "recommendationTag", "BALANCED_STRUCTURE",
                "tradeoff", "Base rent protects downside while sharing utilization upside; requires 25% Host co-investment. [SYNTHETIC DEMO DATA — NO AFFILIATION]", "demoScenario", true));
        deals.add(linkedMap("company", "Vidyut Partner Demo", "chargerPowerKw", 120,
                "revenueModel", "REVENUE_SHARE", "hostShareLabel", "50/50 Split",
                "hostRevenueSharePercent", 50, "installationFunding", "SHARED",
                "maintenanceResponsibility", "SHARED", "expectedSessionsPerMonth", 1_100,
                "projectedMonthlyHostIncome", 39_600, "projectedAnnualHostRevenue", 475_200,
                "projectedThreeYearValue", 1_425_600, "riskLevel", "MEDIUM_LOW",
                "recommendationTag", "CO_OPERATIVE_MODEL",
                "tradeoff", "Balanced partnership model for high-traffic highway hubs. [SYNTHETIC DEMO DATA — NO AFFILIATION]", "demoScenario", true));
        return deals;
    }

    private Map<String, Object> solarOpportunity(List<ChargingStation> stations, List<Booking> bookings) {
        ChargingStation station = stations.stream().filter(item -> item.getAmenities() != null
                && item.getAmenities().toLowerCase(Locale.ROOT).contains("solar")).findFirst()
                .orElse(stations.isEmpty() ? null : stations.get(0));
        if (station == null) return linkedMap("stationId", null, "stationName", "No station selected",
                "solarContributionPercent", 0, "monthlySavings", 0, "eligibilityLeads", List.of(),
                "solarOptions", List.of(), "fundingPlan", Map.of());
        double recordedEnergy = bookings.stream().filter(booking -> booking.getStationId().equals(station.getId())
                && booking.getStatus() == BookingStatus.COMPLETED && booking.getStartTime() != null
                && booking.getStartTime().isAfter(LocalDateTime.now().minusDays(30)))
                .mapToDouble(Booking::getKwhDelivered).sum();
        double modeledConsumption = Math.max(recordedEnergy, station.isDemoData() ? 21_000 : recordedEnergy);
        double solarCapacityKw = station.getAmenities() != null
                && station.getAmenities().toLowerCase(Locale.ROOT).contains("solar") ? 70 : 10;
        double generation = solarCapacityKw >= 70 ? 8_300 : round(solarCapacityKw * 4.5 * 30 * .8);
        double solarUsed = round(Math.min(modeledConsumption, generation));
        double contribution = modeledConsumption <= 0 ? 0 : round(solarUsed * 100 / modeledConsumption);
        double savings = round(solarUsed * 8.8);
        double capex = solarCapacityKw >= 70 ? 2_700_000 : solarCapacityKw * 55_000;
        return linkedMap("stationId", station.getId(), "stationName", station.getName(),
                "propertyOwnerName", Objects.toString(station.getPropertyOwnerName(), "Prince"),
                "operatorCompanyName", Objects.toString(station.getOperatorCompanyName(), "Host operated"),
                "solarProviderName", Objects.toString(station.getSolarProviderName(), "Provider not selected"),
                "modeledMonthlyConsumptionKwh", round(modeledConsumption), "solarCapacityKw", solarCapacityKw,
                "modeledMonthlyGenerationKwh", generation, "solarContributionPercent", contribution,
                "monthlySavings", savings, "simplePaybackYears", savings <= 0 ? 0 : round(capex / savings / 12),
                "dataBasis", recordedEnergy >= modeledConsumption ? "RECORDED_ENERGY" : "DEMO_CORRIDOR_ASSUMPTION",
                "eligibilityLeads", approvedSchemeLeads(),
                "solarOptions", List.of(
                        linkedMap("option", "PURCHASE", "label", "Prince buys solar", "upfrontRequirement", "HIGH",
                                "modeledInvestment", 2_700_000, "monthlyPayment", 0, "ownership", "PRINCE",
                                "tradeoff", "Highest long-term savings, highest upfront capital."),
                        linkedMap("option", "FINANCE", "label", "Solar loan", "upfrontRequirement", "MEDIUM",
                                "modeledInvestment", 270_000, "monthlyPayment", 42_000, "ownership", "PRINCE_AFTER_FINANCE",
                                "tradeoff", "Lower upfront requirement with modeled monthly EMI."),
                        linkedMap("option", "RESCO_PPA", "label", "RESCO / solar PPA", "upfrontRequirement", "LOW",
                                "modeledInvestment", 100_000, "monthlyPayment", 0, "ownership", "SOLAR_PROVIDER",
                                "tradeoff", "Low/no plant purchase; Prince buys generated power at a contracted tariff.")),
                "fundingPlan", linkedMap("projectCost", 3_500_000, "princeBudget", 1_000_000,
                        "princeContribution", 1_000_000, "operatorContribution", 1_750_000,
                        "potentialGovernmentAssistance", 700_000, "solarProviderContribution", 50_000,
                        "solarStructure", "RESCO_PPA",
                        "additionalUpfrontRequired", 0, "withinPrinceBudget", true,
                        "status", "MODELED_NOT_APPROVED"),
                "legalNotice", "These are leads, not guaranteed subsidies. Eligibility and current official terms must be verified before submission.");
    }

    private List<Map<String, Object>> approvedSchemeLeads() {
        var schemes = greenSchemeRepository.findByStatusOrderByUpdatedAtDesc(GreenSchemeStatus.ACTIVE);
        if (schemes.isEmpty()) return List.of(linkedMap("name", "No Admin-approved scheme source",
                "status", "AWAITING_VERIFIED_SOURCE", "potentialAmount", 0,
                "note", "A Finance Admin must add and verify an official source before eligibility is suggested."));
        return schemes.stream().<Map<String, Object>>map(item -> linkedMap("name", item.getName(), "authority", item.getAuthority(),
                "status", "VERIFY_HOST_ELIGIBILITY", "potentialAmount", 0, "note", item.getSummary(),
                "sourceUrl", item.getSourceUrl(), "validUntil", item.getValidUntil())).toList();
    }

    private ChargingStation busiestStation(List<ChargingStation> stations, List<Booking> bookings) {
        Map<Long, Long> counts = bookings.stream().collect(java.util.stream.Collectors.groupingBy(
                Booking::getStationId, java.util.stream.Collectors.counting()));
        return stations.stream().max(Comparator.comparingLong(station -> counts.getOrDefault(station.getId(), 0L)))
                .orElse(null);
    }

    private boolean containsIssueLanguage(String comment) {
        if (comment == null) return false;
        String normalized = comment.toLowerCase(Locale.ROOT);
        return normalized.contains("fail") || normalized.contains("loose") || normalized.contains("slow")
                || normalized.contains("broken") || normalized.contains("heat") || normalized.contains("unsafe");
    }

    public byte[] exportReport(Long accountId, String reportType, String format) {
        Map<String, Object> data = "EARNINGS".equalsIgnoreCase(reportType) ? earnings(accountId) : dashboard(accountId);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Vidyut Host Report", reportType.toUpperCase(Locale.ROOT)));
        rows.add(List.of("Generated", LocalDateTime.now().toString()));
        data.forEach((key, value) -> rows.add(List.of(key, String.valueOf(value))));
        return "PDF".equalsIgnoreCase(format) ? pdf(rows) : xlsx(rows);
    }

    private HostProfile requireHost(Long accountId) {
        HostProfile profile = hostProfileRepository.findById(accountId)
                .orElseThrow(() -> new ForbiddenException("Host workspace is not available for this account"));
        if (!profile.getAccount().isEnabled())
            throw new ForbiddenException("Host account is disabled");
        return profile;
    }

    private HostProfile requireOperationalHost(Long accountId) {
        HostProfile profile = requireHost(accountId);
        if (!profile.getAccount().isEmailVerified())
            throw new ForbiddenException("Verify your email before managing chargers");
        if (!profile.isVerified() || profile.getVerificationStatus() != HostVerificationStatus.VERIFIED)
            throw new ForbiddenException("Host KYC must be approved before managing chargers");
        return profile;
    }

    private void assertHostOperates(ChargingStation station) {
        if (station.getOperatorCompanyId() != null || station.getSupplierCompanyId() != null) {
            throw new ForbiddenException("This charger is operated by the Company. The Host may inspect it and request maintenance, but cannot change its operational state.");
        }
    }

    private ChargingStation ownedStation(Long accountId, Long stationId) {
        return stationRepository.findByIdAndHostUserId(stationId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger location not found for this host"));
    }

    private ChargingConnector ownedConnector(Long accountId, Long connectorId) {
        return connectorRepository.findByIdAndStation_HostUserId(connectorId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Connector not found for this host"));
    }

    private Booking ownedBooking(Long accountId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        ownedStation(accountId, booking.getStationId());
        return booking;
    }

    private HostReview ownedReview(Long accountId, Long reviewId) {
        return reviewRepository.findByIdAndHostAccountId(reviewId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found for this host"));
    }

    private List<Booking> ownedBookings(Long accountId) {
        return bookingsFor(stationRepository.findByHostUserId(accountId));
    }

    private List<Booking> bookingsFor(List<ChargingStation> stations) {
        List<Long> ids = stations.stream().map(ChargingStation::getId).toList();
        return ids.isEmpty() ? List.of() : bookingRepository.findByStationIdInOrderByStartTimeDesc(ids);
    }

    private HostBookingResponse mapBooking(Booking booking) {
        Account customer = accountRepository.findById(booking.getUserId()).orElse(null);
        String name = evUserProfileRepository.findById(booking.getUserId()).map(EvUserProfile::getFullName)
                .orElse("EV customer");
        return HostBookingResponse.builder().id(booking.getId()).stationId(booking.getStationId())
                .stationName(booking.getStationName())
                .customerAccountId(booking.getUserId()).customerName(name)
                .customerEmail(customer == null ? null : customer.getEmail())
                .startTime(booking.getStartTime()).durationHours(booking.getDurationHours())
                .totalAmount(booking.getTotalAmount())
                .kwhDelivered(booking.getKwhDelivered()).status(booking.getStatus()).build();
    }

    private Map<String, Object> mapConnector(ChargingConnector connector) {
        return mapConnector(connector, null);
    }

    private Map<String, Object> mapConnector(ChargingConnector connector, ChargingSession session) {
        long duration = connector.getSessionStartedAt() == null ? 0
                : Duration.between(connector.getSessionStartedAt(), LocalDateTime.now()).toMinutes();
        Vehicle vehicle = session == null || session.getVehicleId() == null ? null
                : vehicleRepository.findById(session.getVehicleId()).orElse(null);
        long remainingMinutes = session == null || session.getEstimatedCompletionAt() == null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), session.getEstimatedCompletionAt()).toMinutes());
        String effectiveStatus = session == null ? connector.getStatus().name() : ChargerStatus.CHARGING.name();
        return linkedMap("id", connector.getId(), "stationId", connector.getStation().getId(), "stationName",
                connector.getStation().getName(),
                "operatorCompanyName", Objects.toString(connector.getStation().getOperatorCompanyName(), "Host operated"),
                "canControlOperationalStatus", connector.getStation().getOperatorCompanyId() == null && connector.getStation().getSupplierCompanyId() == null,
                "chargerCode", connector.getChargerCode(), "connectorType", connector.getType(), "powerKw",
                connector.getPowerKw(),
                "status", effectiveStatus, "availabilityLabel", session == null && connector.isAvailable() ? "AVAILABLE" :
                        session != null ? "OCCUPIED" : effectiveStatus,
                "available", session == null && connector.isAvailable(), "currentPowerKw",
                session == null ? connector.getCurrentPowerKw() : session.getPowerKw(),
                "sessionEnergyKwh", session == null ? connector.getSessionEnergyKwh() : session.getEnergyKwh(),
                "sessionDurationMinutes", session == null ? duration : Math.max(0,
                        Duration.between(session.getStartedAt(), LocalDateTime.now()).toMinutes()),
                "sessionId", session == null ? null : session.getId(),
                "bookingId", session == null ? null : session.getBookingId(),
                "vehicleId", session == null ? null : session.getVehicleId(),
                "vehicleName", vehicle == null ? null : vehicle.getMakeAndModel(),
                "vehicleRegistration", vehicle == null ? null : vehicle.getRegistrationNumber(),
                "startBatteryPercent", session == null ? null : session.getStartBatteryPercent(),
                "currentBatteryPercent", session == null ? null : session.getCurrentBatteryPercent(),
                "targetBatteryPercent", session == null ? null : session.getTargetBatteryPercent(),
                "estimatedCompletionAt", session == null ? null : session.getEstimatedCompletionAt(),
                "remainingMinutes", remainingMinutes,
                "sessionCost", session == null ? 0 : session.getCost(),
                "healthScore",
                connector.getHealthScore(),
                "faultCode", connector.getFaultCode(), "lastHeartbeat", connector.getLastHeartbeat());
    }

    private HostProfileResponse mapProfile(HostProfile profile) {
        return HostProfileResponse.builder().accountId(profile.getAccountId()).email(profile.getAccount().getEmail())
                .emailVerified(profile.getAccount().isEmailVerified()).displayName(profile.getDisplayName())
                .phone(profile.getPhone())
                .address(profile.getAddress()).bio(profile.getBio()).verificationStatus(profile.getVerificationStatus())
                .kycDocumentUrl(profile.getKycDocumentUrl()).identityType(profile.getIdentityType())
                .identityLast4(profile.getIdentityLast4())
                .verificationRequestedAt(profile.getVerificationRequestedAt())
                .bankAccountHolder(profile.getBankAccountHolder())
                .bankName(profile.getBankName()).bankAccountLast4(profile.getBankAccountLast4())
                .ifscCode(profile.getIfscCode())
                .payoutUpi(profile.getPayoutUpi()).bankVerified(profile.isBankVerified())
                .emailNotifications(profile.isEmailNotifications())
                .pushNotifications(profile.isPushNotifications()).autoAvailability(profile.isAutoAvailability())
                .reputationScore(profile.getReputationScore()).build();
    }

    private double revenueSince(List<Booking> bookings, LocalDate date) {
        return round(bookings.stream()
                .filter(b -> b.getStartTime() != null && !b.getStartTime().toLocalDate().isBefore(date))
                .mapToDouble(Booking::getTotalAmount).sum());
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to secure verification code", exception);
        }
    }

    private boolean isAllowedHostTransition(BookingStatus current, BookingStatus next) {
        if (current == null || next == null) return false;
        return switch (current) {
            case PENDING -> next == BookingStatus.CONFIRMED || next == BookingStatus.CANCELLED;
            case CONFIRMED -> next == BookingStatus.IN_PROGRESS || next == BookingStatus.CANCELLED;
            case IN_PROGRESS -> next == BookingStatus.COMPLETED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() == 12 && digits.startsWith("91") ? digits.substring(2) : digits;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double latitude = Math.toRadians(lat2 - lat1);
        double longitude = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latitude / 2) * Math.sin(latitude / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitude / 2) * Math.sin(longitude / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private LinkedHashMap<String, Object> linkedMap(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2)
            map.put(String.valueOf(values[index]), values[index + 1]);
        return map;
    }

    private byte[] pdf(List<List<String>> rows) {
        try {
            StringBuilder text = new StringBuilder("BT /F1 11 Tf 40 800 Td ");
            for (List<String> row : rows)
                text.append('(').append(escapePdf(String.join(": ", row))).append(") Tj 0 -17 Td ");
            text.append("ET");
            byte[] stream = text.toString().getBytes(StandardCharsets.ISO_8859_1);
            List<String> objects = List.of("<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                    "<< /Length " + stream.length + " >>\nstream\n" + new String(stream, StandardCharsets.ISO_8859_1)
                            + "\nendstream",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(out.size());
                out.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
            }
            int xref = out.size();
            out.write(("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            for (int offset : offsets)
                out.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
            out.write(("trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF")
                    .getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create PDF", exception);
        }
    }

    private byte[] xlsx(List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zipEntry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            zipEntry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            zipEntry(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Host Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            zipEntry(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet = new StringBuilder(
                    "<?xml version=\"1.0\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for (int r = 0; r < rows.size(); r++) {
                sheet.append("<row r=\"").append(r + 1).append("\">");
                for (int c = 0; c < rows.get(r).size(); c++)
                    sheet.append("<c r=\"").append((char) ('A' + c)).append(r + 1).append("\" t=\"inlineStr\"><is><t>")
                            .append(xml(rows.get(r).get(c))).append("</t></is></c>");
                sheet.append("</row>");
            }
            sheet.append("</sheetData></worksheet>");
            zipEntry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            zip.finish();
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create Excel report", exception);
        }
    }

    private void zipEntry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("₹", "INR ");
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
