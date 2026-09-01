package com.vidyut.host.config;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.entity.HostVerificationStatus;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.repository.HostReviewRepository;
import com.vidyut.session.entity.ChargingSession;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import com.vidyut.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class PrinceHostDemoInitializer implements ApplicationRunner {

    private static final String HOST_ID = "HOST-PRINCE-01";

    private final AccountRepository accountRepository;
    private final HostProfileRepository hostProfileRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final ChargingStationRepository stationRepository;
    private final BookingRepository bookingRepository;
    private final HostReviewRepository reviewRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingSessionRepository sessionRepository;
    private final com.vidyut.company.repository.CompanyRepository companyRepository;
    private final com.vidyut.station.repository.ChargingConnectorRepository connectorRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${vidyut.demo-data.prince-email:prince@vidyut.demo}")
    private String princeEmail;

    @Value("${vidyut.demo-data.prince-password:PrinceDemo123!}")
    private String princePassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Account prince = ensureAccount(princeEmail, princePassword, AccountRole.ROLE_HOST);
        ensurePrinceProfile(prince);
        com.vidyut.company.entity.Company tataCompany = ensureTataCompany();
        Map<String, ChargingStation> corridor = seedCorridor(prince.getId(), tataCompany);
        Account priyanshu = ensureAccount("priyanshu@vidyut.demo", "Priyanshu123!", AccountRole.ROLE_EV_USER);
        ensurePriyanshuProfile(priyanshu);
        ensurePriyanshuVehicle(priyanshu.getId());
        Account driver = ensureAccount("corridor.driver@vidyut.demo", "CorridorDemo123!", AccountRole.ROLE_EV_USER);
        ensureDriverProfile(driver);
        seedCustomerSignals(prince.getId(), driver.getId(), corridor);
        seedLiveSessions(driver.getId(), corridor);
        connectorRepository.findAll().stream()
                .filter(c -> c.getStatus() == ChargerStatus.SUSPECTED_FAULT && c.getChargerCode() != null && c.getChargerCode().startsWith("DIST-SOI"))
                .forEach(c -> {
                    c.setStatus(ChargerStatus.ONLINE);
                    c.setAvailable(true);
                    connectorRepository.save(c);
                });
    }

    private com.vidyut.company.entity.Company ensureTataCompany() {
        String companyEmail = "tata@vidyut.demo";
        Account companyAccount = accountRepository.findByEmailIgnoreCase(companyEmail)
                .or(() -> accountRepository.findByEmailIgnoreCase("contactpriyanshusharma6281@gmail.com"))
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .email(companyEmail)
                        .passwordHash(passwordEncoder.encode("TataDemo123!"))
                        .accountType(AccountType.COMPANY)
                        .roles(new HashSet<>(Set.of(AccountRole.ROLE_COMPANY)))
                        .enabled(true)
                        .emailVerified(true)
                        .build()));
        if (companyAccount.getAccountType() != AccountType.COMPANY) {
            companyAccount.setAccountType(AccountType.COMPANY);
        }
        if (!companyAccount.getRoles().contains(AccountRole.ROLE_COMPANY)) {
            companyAccount.setRoles(new HashSet<>(Set.of(AccountRole.ROLE_COMPANY)));
        }
        companyAccount.setPasswordHash(passwordEncoder.encode("TataDemo123!"));
        final Account finalCompanyAccount = accountRepository.save(companyAccount);

        com.vidyut.company.entity.Company company = companyRepository.findByAccount_Id(finalCompanyAccount.getId())
                .or(() -> companyRepository.findAll().stream().filter(c -> "TATA Power".equalsIgnoreCase(c.getCompanyName())).findFirst())
                .orElseGet(() -> companyRepository.save(com.vidyut.company.entity.Company.builder()
                        .account(finalCompanyAccount)
                        .companyName("TATA Power")
                        .registrationNumber("U99999UP2026PTC062811")
                        .contactName("Tata Demo Administrator")
                        .supportEmail(companyEmail)
                        .supportPhone("+919000000000")
                        .active(true)
                        .verificationStatus(com.vidyut.company.entity.VerificationStatus.VERIFIED)
                        .emailNotifications(true)
                        .pushNotifications(true)
                        .timezone("Asia/Kolkata")
                        .build()));
        company.setAccount(finalCompanyAccount);
        company.setCompanyName("TATA Power");
        company.setActive(true);
        company.setVerificationStatus(com.vidyut.company.entity.VerificationStatus.VERIFIED);
        return companyRepository.save(company);
    }

    private Account ensureAccount(String email, String password, AccountRole role) {
        AccountType targetType = role == AccountRole.ROLE_COMPANY ? AccountType.COMPANY : (role == AccountRole.ROLE_ADMIN ? AccountType.ADMIN : AccountType.INDIVIDUAL);
        Account account = accountRepository.findByEmailIgnoreCase(email).orElseGet(() -> accountRepository.save(
                Account.builder()
                        .email(email.toLowerCase())
                        .passwordHash(passwordEncoder.encode(password))
                        .accountType(targetType)
                        .roles(new HashSet<>(Set.of(role)))
                        .enabled(true)
                        .emailVerified(true)
                        .build()));
        if (account.getAccountType() != targetType) {
            account.setAccountType(targetType);
        }
        if (!account.getRoles().contains(role)) {
            account.getRoles().add(role);
        }
        account.setEnabled(true);
        account.setEmailVerified(true);
        return accountRepository.save(account);
    }

    private void ensurePrinceProfile(Account account) {
        HostProfile profile = hostProfileRepository.findById(account.getId()).orElseGet(() -> HostProfile.builder()
                .account(account)
                .displayName("Prince")
                .build());
        profile.setDisplayName("Prince");
        profile.setPhone("9000000101");
        profile.setVerified(true);
        profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
        profile.setBio(HOST_ID + " · Property Host for a multi-operator EV charging portfolio");
        profile.setAddress("Lucknow–Kanpur–Jhansi–Bhopal corridor · Agra solar property");
        profile.setBankVerified(true);
        profile.setBankAccountHolder("Prince");
        profile.setBankName("Vidyut Demo Bank");
        profile.setBankAccountLast4("0101");
        profile.setIfscCode("VIDY0000101");
        profile.setAutoAvailability(true);
        profile.setReputationScore(4.6);
        hostProfileRepository.save(profile);
    }

    private void ensurePriyanshuProfile(Account account) {
        EvUserProfile profile = evUserProfileRepository.findById(account.getId()).orElseGet(() ->
                EvUserProfile.builder().account(account).build());
        profile.setFullName("Priyanshu Sharma");
        profile.setPhone("9000000002");
        evUserProfileRepository.save(profile);
    }

    private void ensurePriyanshuVehicle(Long userId) {
        if (vehicleRepository.findByUserId(userId).isEmpty()) {
            vehicleRepository.save(com.vidyut.vehicle.entity.Vehicle.builder()
                    .userId(userId)
                    .makeAndModel("Tata Nexon EV Long Range")
                    .registrationNumber("DL01EV2026")
                    .batteryCapacity("45 kWh")
                    .connectorType("CCS2")
                    .supportedConnectors(new java.util.LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)))
                    .efficiencyWhPerKm(155.0)
                    .maxAcChargePowerKw(7.2)
                    .maxDcChargePowerKw(60.0)
                    .chargingEfficiency(0.90)
                    .batteryPercent(88)
                    .remainingRangeKm(255.0)
                    .connectionStatus(com.vidyut.vehicle.entity.VehicleConnectionStatus.CONNECTED)
                    .telemetrySource(com.vidyut.vehicle.entity.VehicleTelemetrySource.MANUAL)
                    .charging(false)
                    .build());
        }
    }

    private void ensureDriverProfile(Account account) {
        if (evUserProfileRepository.findById(account.getId()).isEmpty()) {
            evUserProfileRepository.save(EvUserProfile.builder()
                    .account(account)
                    .fullName("Corridor Demo Driver")
                    .phone("9000000001")
                    .build());
        }
    }

    private Map<String, ChargingStation> seedCorridor(Long hostId, com.vidyut.company.entity.Company tataCompany) {
        Map<String, ChargingStation> existing = stationRepository.findAll().stream()
                .filter(station -> station.getDemoSeedKey() != null)
                .collect(Collectors.toMap(ChargingStation::getDemoSeedKey, Function.identity(), (first, ignored) -> first));
        List<StationSeed> seeds = List.of(
                new StationSeed("host-prince-dausa", "Prince Highway Charging Hub",
                        "NH-21 Jaipur-Agra Highway, Dausa, Rajasthan", "Dausa", 26.8008, 76.4209, 17.5,
                        "Cafe, Restroom, CCTV, 24x7 Highway parking", "Prince", hostId,
                        "Tata Power — Demo Operator Data", tataCompany.getId(), null,
                        List.of(new ConnectorSeed("TATA-DAU-01", ConnectorType.CCS2, 120, 95, null),
                                new ConnectorSeed("TATA-DAU-02", ConnectorType.CCS2, 120, 96, null))),
                new StationSeed("host-aditi-noida", "Noida Express Charging Hub",
                        "Noida-Greater Noida Expressway, Sector 132, Noida, Uttar Pradesh", "Noida", 28.5355, 77.3910, 16.5,
                        "Restroom, Food Court, Wi-Fi, 24x7 Security", "Host Aditi", null,
                        "Tata Power — Demo Operator Data", tataCompany.getId(), null,
                        List.of(new ConnectorSeed("TATA-NOI-01", ConnectorType.CCS2, 120, 98, null),
                                new ConnectorSeed("TATA-NOI-02", ConnectorType.CCS2, 120, 97, null))),
                new StationSeed("alwar-express-recovery", "Alwar Express Recovery Hub",
                        "Delhi-Mumbai Expressway Bypass, Alwar, Rajasthan", "Alwar", 27.5530, 76.6346, 16.8,
                        "Restroom, Safe Seating, CCTV, 24x7 Highway Support", "Highway Recovery Host", null,
                        "ChargeZone Demo", null, null,
                        List.of(new ConnectorSeed("CZ-ALW-01", ConnectorType.CCS2, 120, 98, null),
                                new ConnectorSeed("CZ-ALW-02", ConnectorType.CCS2, 120, 97, null))),
                new StationSeed("host-rahul-sawai-madhopur", "Sawai Madhopur Highway Property",
                        "Ranthambore Road Corridor, Sawai Madhopur, Rajasthan", "Sawai Madhopur", 25.9928, 76.3526, 18.0,
                        "Cafe, Restroom, Parking, Wi-Fi", "Host Rahul", null,
                        "Statiq Demo", null, null,
                        List.of(new ConnectorSeed("STATIQ-SWM-01", ConnectorType.CCS2, 120, 96, null),
                                new ConnectorSeed("STATIQ-SWM-02", ConnectorType.CCS2, 120, 95, null))),
                new StationSeed("host-neha-kota", "Kota Highway Charging Hub",
                        "Jhalawar Road, Kota, Rajasthan", "Kota", 25.1825, 75.8391, 17.0,
                        "Food Court, Restroom, CCTV, 24x7 Security", "Host Neha", null,
                        "Tata Power — Demo Operator Data", tataCompany.getId(), null,
                        List.of(new ConnectorSeed("TATA-KTA-01", ConnectorType.CCS2, 150, 96, null),
                                new ConnectorSeed("TATA-KTA-02", ConnectorType.CCS2, 150, 95, null))),
                new StationSeed("host-bhopal-city-hub", "Bhopal City Central Hub",
                        "Hoshangabad Road, Bhopal, Madhya Pradesh", "Bhopal", 23.1870, 77.4330, 16.5,
                        "Food court, Restroom, CCTV, 24x7 security", "MP State Host", null,
                        "ChargeZone Demo", null, null,
                        List.of(new ConnectorSeed("CZ-BPL-01", ConnectorType.CCS2, 180, 97, null),
                                new ConnectorSeed("CZ-BPL-02", ConnectorType.CCS2, 180, 96, null)))
        );

        for (StationSeed seed : seeds) {
            ChargingStation station = existing.get(seed.key());
            if (station == null) {
                station = ChargingStation.builder()
                        .name(seed.name()).address(seed.address()).city(seed.city())
                        .latitude(seed.latitude()).longitude(seed.longitude()).pricePerKwh(seed.pricePerKwh())
                        .rating(4.6).reviewCount(84).amenities(seed.amenities())
                        .workingHours("Open 24 hours").weeklySchedule("MON-SUN 00:00-24:00")
                        .chargingInstructions("DEMO DATA · Corridor reservations supported by Vidyut Autopilot")
                        .autoAvailability(true).bookingSlotMinutes(30).queueCount(0).occupancyPercent(48)
                        .status(StationStatus.ACTIVE).availability(StationAvailability.AVAILABLE)
                        .hostUserId(seed.hostUserId()).propertyOwnerAccountId(seed.hostUserId())
                        .ownershipType(seed.hostUserId() != null ? StationOwnershipType.HOST_PARTNERED : StationOwnershipType.COMPANY_OWNED)
                        .demoData(true).demoSeedKey(seed.key())
                        .connectors(new ArrayList<>()).build();
                applyOperatingParties(station, seed);
                station = stationRepository.save(station);
                syncConnectors(station, seed.connectors());
                station = stationRepository.save(station);
            } else {
                station.setHostUserId(seed.hostUserId());
                station.setPropertyOwnerAccountId(seed.hostUserId());
                station.setOwnershipType(seed.hostUserId() != null ? StationOwnershipType.HOST_PARTNERED : StationOwnershipType.COMPANY_OWNED);
                station.setDemoData(true);
                station.setName(seed.name());
                station.setAddress(seed.address());
                station.setCity(seed.city());
                station.setLatitude(seed.latitude());
                station.setLongitude(seed.longitude());
                station.setPricePerKwh(seed.pricePerKwh());
                station.setRating(4.8);
                station.setQueueCount(0);
                station.setOccupancyPercent(seed.solarProvider() != null ? 24 : 36);
                station.setAmenities(seed.amenities());
                station.setChargingInstructions("DEMO DATA · Corridor reservations supported by Vidyut Autopilot");
                applyOperatingParties(station, seed);
                station = stationRepository.save(station);
                syncConnectors(station, seed.connectors());
                station = stationRepository.save(station);
            }
            existing.put(seed.key(), station);
        }
        return existing;
    }

    private void applyOperatingParties(ChargingStation station, StationSeed seed) {
        station.setPropertyOwnerName(seed.propertyOwnerName());
        station.setOperatorCompanyId(seed.operatorCompanyId());
        station.setOperatorCompanyName(seed.operatorCompany());
        station.setEquipmentOwnerName(seed.operatorCompany());
        station.setOperatingModel(seed.hostUserId() != null ? "HOST_PROPERTY_CPO_EQUIPMENT" : "CPO_OPERATED");
        station.setSolarProviderName(seed.solarProvider());
    }

    private void syncConnectors(ChargingStation station, List<ConnectorSeed> seeds) {
        if (station.getConnectors() == null) station.setConnectors(new ArrayList<>());
        List<ChargingConnector> updated = new ArrayList<>();
        for (ConnectorSeed seed : seeds) {
            ChargingConnector connector = connectorRepository.findByChargerCode(seed.code())
                    .orElseGet(() -> ChargingConnector.builder()
                            .station(station)
                            .chargerCode(seed.code())
                            .available(true)
                            .status(ChargerStatus.ONLINE)
                            .maintenanceMode(false)
                            .build());
            connector.setStation(station);
            connector.setChargerCode(seed.code());
            connector.setType(seed.type());
            connector.setPowerKw(seed.powerKw());
            connector.setHealthScore(seed.healthScore());
            connector.setStatus(ChargerStatus.ONLINE);
            connector.setAvailable(true);
            connector.setMaintenanceMode(false);
            connector.setFaultCode(seed.customerSignal());
            connector.setFirmwareVersion("prince-multi-operator-demo-2.0");
            updated.add(connector);
        }
        station.getConnectors().clear();
        station.getConnectors().addAll(updated);
    }

    private void seedCustomerSignals(Long hostId, Long driverId, Map<String, ChargingStation> stations) {
        ChargingStation dausa = stations.get("host-prince-dausa");
        if (dausa == null) return;
        List<ChargingStation> routeStations = List.of(dausa);
        for (int index = 0; index < 8; index++) {
            ChargingStation station = routeStations.get(index % routeStations.size());
            String key = "PRINCE-DEMO-SESSION-" + index;
            int daysAgo = index % 5;
            LocalDateTime start = daysAgo == 0
                    ? LocalDateTime.now().minusHours(1 + index % 3).withMinute(0).withSecond(0).withNano(0)
                    : LocalDateTime.now().minusDays(daysAgo).withHour(17 + index % 4).withMinute(0).withSecond(0).withNano(0);
            Booking booking = bookingRepository.findByUserIdAndIdempotencyKey(driverId, key)
                    .orElseGet(() -> Booking.builder()
                            .userId(driverId).vehicleId(null).idempotencyKey(key).build());
            booking.setStationId(station.getId());
            booking.setStationName(station.getName());
            booking.setStationAddress(station.getAddress());
            booking.setStartTime(start);
            booking.setEndTime(start.plusMinutes(38));
            booking.setDurationHours(1);
            booking.setDurationMinutes(38);
            booking.setTotalAmount(118 + (index % 5) * 19);
            booking.setKwhDelivered(10 + (index % 4) * 2.5);
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setSeen(true);
            bookingRepository.save(booking);
        }
        Set<Long> reviewedBookings = reviewRepository.findByHostAccountIdOrderByCreatedAtDesc(hostId).stream()
                .map(HostReview::getBookingId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        bookingRepository.findByStationId(dausa.getId()).stream().limit(4).forEach(booking -> {
            if (reviewedBookings.contains(booking.getId())) return;
            reviewRepository.save(HostReview.builder()
                    .hostAccountId(hostId).stationId(dausa.getId()).bookingId(booking.getId())
                    .customerAccountId(driverId).customerName("Corridor Demo Driver")
                    .rating(4).comment("CCS2 fast charging hub on the Jaipur-Agra expressway. DEMO DATA.")
                    .createdAt(LocalDateTime.now().minusDays(1)).build());
        });
    }

    private void seedLiveSessions(Long driverId, Map<String, ChargingStation> stations) {
        List<LiveSessionSeed> seeds = List.of(
                new LiveSessionSeed("host-prince-dausa", "TATA-DAU-02", "Tata Nexon EV Demo",
                        "RJ14LIV001", 45.0, 60.0, 42, 68, 80, 14, 12, 16.4, 52.0),
                new LiveSessionSeed("host-aditi-noida", "TATA-NOI-01", "MG ZS EV Demo",
                        "UP16LIV002", 50.3, 80.0, 31, 55, 80, 16, 18, 18.2, 60.0),
                new LiveSessionSeed("host-neha-kota", "TATA-KTA-01", "Mahindra XUV400 Demo",
                        "RJ20LIV003", 39.4, 50.0, 48, 71, 90, 20, 15, 14.8, 44.0));

        for (int index = 0; index < seeds.size(); index++) {
            LiveSessionSeed seed = seeds.get(index);
            ChargingStation station = stations.get(seed.stationKey());
            if (station == null) continue;
            ChargingConnector connector = station.getConnectors().stream()
                    .filter(item -> seed.connectorCode().equals(item.getChargerCode()))
                    .findFirst().orElse(null);
            if (connector == null) continue;

            Vehicle vehicle = vehicleRepository.findAll().stream()
                    .filter(item -> seed.registration().equalsIgnoreCase(item.getRegistrationNumber()))
                    .findFirst().orElseGet(() -> Vehicle.builder()
                            .userId(driverId)
                            .makeAndModel(seed.vehicleName())
                            .registrationNumber(seed.registration())
                            .build());
            vehicle.setUserId(driverId);
            vehicle.setMakeAndModel(seed.vehicleName());
            vehicle.setBatteryCapacity(String.format(java.util.Locale.ROOT, "%.1f kWh", seed.batteryCapacityKwh()));
            vehicle.setConnectorType(ConnectorType.CCS2.name());
            vehicle.setSupportedConnectors(new java.util.LinkedHashSet<>(Set.of(ConnectorType.CCS2, ConnectorType.TYPE2)));
            vehicle.setEfficiencyWhPerKm(155.0);
            vehicle.setMaxAcChargePowerKw(11.0);
            vehicle.setMaxDcChargePowerKw(seed.vehicleMaxDcKw());
            vehicle.setChargingEfficiency(0.90);
            vehicle.setConnectionStatus(VehicleConnectionStatus.CONNECTED);
            vehicle.setBatteryPercent(seed.currentSoc());
            vehicle.setRemainingRangeKm(Math.round(seed.currentSoc() * seed.batteryCapacityKwh() * 10.0 / 1.55) / 100.0);
            vehicle.setCharging(true);
            vehicle.setLastChargingStation(station.getName());
            vehicle.setLastChargingAddress(station.getAddress());
            vehicle.setLastChargedAt(LocalDateTime.now());
            vehicle.setTelemetrySource(VehicleTelemetrySource.CHARGING_SESSION);
            vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
            vehicle = vehicleRepository.save(vehicle);

            LocalDateTime startedAt = LocalDateTime.now().minusMinutes(seed.elapsedMinutes());
            LocalDateTime completionAt = LocalDateTime.now().plusMinutes(seed.remainingMinutes());
            String idempotencyKey = "PRINCE-LIVE-SESSION-" + index;
            Booking booking = bookingRepository.findByUserIdAndIdempotencyKey(driverId, idempotencyKey)
                    .orElseGet(() -> Booking.builder().userId(driverId).idempotencyKey(idempotencyKey).build());
            booking.setVehicleId(vehicle.getId());
            booking.setStationId(station.getId());
            booking.setStationName(station.getName());
            booking.setStationAddress(station.getAddress());
            booking.setStartTime(startedAt);
            booking.setEndTime(completionAt);
            booking.setDurationHours(1);
            booking.setDurationMinutes(seed.elapsedMinutes() + seed.remainingMinutes());
            booking.setKwhDelivered(Math.max(seed.energyKwh(), seed.batteryCapacityKwh()));
            booking.setTotalAmount(round(seed.energyKwh() * station.getPricePerKwh()));
            booking.setStatus(BookingStatus.IN_PROGRESS);
            booking.setSeen(false);
            booking = bookingRepository.save(booking);
            Long savedBookingId = booking.getId();

            ChargingSession session = sessionRepository.findByBookingIdAndUserId(savedBookingId, driverId)
                    .orElseGet(() -> ChargingSession.builder()
                            .userId(driverId).bookingId(savedBookingId).stationId(station.getId()).build());
            session.setConnectorId(connector.getId());
            session.setVehicleId(vehicle.getId());
            session.setStatus(ChargingSessionStatus.ACTIVE);
            session.setPaymentStatus("DUE");
            session.setPowerKw(seed.powerKw());
            session.setEnergyKwh(seed.energyKwh());
            session.setCost(round(seed.energyKwh() * station.getPricePerKwh()));
            session.setStartBatteryPercent(seed.startSoc());
            session.setCurrentBatteryPercent(seed.currentSoc());
            session.setTargetBatteryPercent(seed.targetSoc());
            session.setStartedAt(startedAt);
            session.setEstimatedCompletionAt(completionAt);
            session.setCompletedAt(null);
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);

            connector.setStatus(ChargerStatus.CHARGING);
            connector.setAvailable(false);
            connector.setMaintenanceMode(false);
            connector.setCurrentPowerKw(seed.powerKw());
            connector.setSessionEnergyKwh(seed.energyKwh());
            connector.setSessionStartedAt(startedAt);
            connector.setLastHeartbeat(LocalDateTime.now());
            connector.setFaultCode(null);
            stationRepository.save(station);
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record StationSeed(String key, String name, String address, String city, double latitude,
                               double longitude, double pricePerKwh, String amenities, String propertyOwnerName,
                               Long hostUserId, String operatorCompany, Long operatorCompanyId,
                               String solarProvider,
                               List<ConnectorSeed> connectors) {}

    private record ConnectorSeed(String code, ConnectorType type, double powerKw, int healthScore,
                                 String customerSignal) {}

    private record LiveSessionSeed(String stationKey, String connectorCode, String vehicleName,
                                   String registration, double batteryCapacityKwh, double vehicleMaxDcKw,
                                   int startSoc, int currentSoc, int targetSoc, int elapsedMinutes,
                                   int remainingMinutes, double energyKwh, double powerKw) {}
}
