package com.vidyut.config;

import com.vidyut.account.entity.AccessMode;
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
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.CompanyAgentMode;
import com.vidyut.company.entity.CompanyMaintenanceTicket;
import com.vidyut.company.entity.MaintenancePriority;
import com.vidyut.company.entity.MaintenanceTicketStatus;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.entity.OwnershipType;
import com.vidyut.land.entity.PowerPhase;
import com.vidyut.land.entity.PropertyType;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.repository.NotificationRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.entity.EvWallet;
import com.vidyut.wallet.repository.EvWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements CommandLineRunner {

    public static final String DEMO_DRIVER_EMAIL = "demo.driver@vidyut.com";
    public static final String DEMO_HOST_EMAIL = "demo.host@vidyut.com";
    public static final String DEMO_COMPANY_EMAIL = "demo.company@vidyut.com";
    public static final String DEMO_EV_REGISTRATION = "DEMO-EV-001";
    public static final String DEMO_COMPANY_NAME = "Tata EV Charging Demo";

    // Designated target connector for hackathon fault / reroute demonstration
    public static final String REROUTE_DEMO_TARGET_CHARGER_CODE = "DEMO-AGRA-CCS2-01";

    @Value("${demo.seed.enabled:false}")
    private boolean enabled;

    @Value("${demo.account.password:}")
    private String demoPassword;

    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final HostProfileRepository hostProfileRepository;
    private final CompanyRepository companyRepository;
    private final EvWalletRepository walletRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final LandListingRepository landListingRepository;
    private final NotificationRepository notificationRepository;
    private final BookingRepository bookingRepository;
    private final CompanyMaintenanceTicketRepository maintenanceTicketRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("DemoDataSeeder is disabled (demo.seed.enabled=false). Skipping demo seeding.");
            return;
        }

        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("DEMO_ACCOUNT_PASSWORD is not set. Demo accounts cannot be seeded without a configured password.");
            return;
        }

        log.info("🚀 Starting restorative and idempotent DemoDataSeeder for Vidyut hackathon environment...");

        Account driverAccount = seedDemoDriver();
        Account hostAccount = seedDemoHost();
        Account companyAccount = seedDemoCompany();
        Company company = companyRepository.findByAccount_Id(companyAccount.getId())
                .orElseThrow(() -> new IllegalStateException("Seeded company profile not found"));

        seedDemoVehicles(driverAccount, "DEMO-EV-");
        List<LandListing> hostProperties = seedDemoHostProperties(hostAccount);
        seedDemoChargingNetwork(company, hostAccount, hostProperties);
        try {
            seedNationwideHighwayHubs(company);
            seedDistrictHubs(company);
        } catch (IOException ex) {
            log.error("Failed to seed nationwide demo network: {}", ex.getMessage(), ex);
        }
        seedDemoBookings(driverAccount);
        seedDemoNotifications(driverAccount, hostAccount);
        seedDemoMaintenanceTickets(company);

        log.info("✅ Vidyut DemoDataSeeder completed successfully. Canonical demo state verified and repaired.");
    }

    private Account seedDemoDriver() {
        Account account = accountRepository.findByEmailIgnoreCase(DEMO_DRIVER_EMAIL).orElse(null);
        if (account == null) {
            account = Account.builder()
                    .email(DEMO_DRIVER_EMAIL)
                    .passwordHash(passwordEncoder.encode(demoPassword))
                    .accountType(AccountType.INDIVIDUAL)
                    .roles(new LinkedHashSet<>(Set.of(AccountRole.ROLE_EV_USER)))
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            account = accountRepository.save(account);
            log.info("Seeded demo EV owner account: {}", DEMO_DRIVER_EMAIL);
        } else {
            account.setPasswordHash(passwordEncoder.encode(demoPassword));
            account.setEnabled(true);
            account.setEmailVerified(true);
            account = accountRepository.save(account);
        }

        final Account savedAccount = account;
        EvUserProfile profile = evUserProfileRepository.findById(account.getId()).orElse(null);
        if (profile == null) {
            profile = EvUserProfile.builder()
                    .account(savedAccount)
                    .fullName("Vidyut Demo Driver")
                    .phone("9876500001")
                    .build();
            evUserProfileRepository.save(profile);
        } else {
            profile.setFullName("Vidyut Demo Driver");
            evUserProfileRepository.save(profile);
        }

        if (walletRepository.findByUserId(account.getId()).isEmpty()) {
            walletRepository.save(EvWallet.builder()
                    .userId(account.getId())
                    .balance(3500.0)
                    .build());
        }

        return account;
    }

    private Account seedDemoHost() {
        Account account = accountRepository.findByEmailIgnoreCase(DEMO_HOST_EMAIL).orElse(null);
        if (account == null) {
            account = Account.builder()
                    .email(DEMO_HOST_EMAIL)
                    .passwordHash(passwordEncoder.encode(demoPassword))
                    .accountType(AccountType.INDIVIDUAL)
                    .roles(new LinkedHashSet<>(Set.of(AccountRole.ROLE_HOST)))
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            account = accountRepository.save(account);
            log.info("Seeded demo Host account: {}", DEMO_HOST_EMAIL);
        } else {
            account.setPasswordHash(passwordEncoder.encode(demoPassword));
            account.setEnabled(true);
            account.setEmailVerified(true);
            account = accountRepository.save(account);
        }

        final Account savedAccount = account;
        HostProfile profile = hostProfileRepository.findById(account.getId()).orElse(null);
        if (profile == null) {
            profile = HostProfile.builder()
                    .account(savedAccount)
                    .displayName("Vidyut Demo Host")
                    .phone("9876500002")
                    .verified(true)
                    .verificationStatus(HostVerificationStatus.VERIFIED)
                    .address("Agra Highway Expressway Plaza, NH-19, Agra")
                    .bio("Certified EV host property partner on key north-central transit corridors.")
                    .build();
            hostProfileRepository.save(profile);
        } else {
            profile.setDisplayName("Vidyut Demo Host");
            profile.setVerified(true);
            profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
            hostProfileRepository.save(profile);
        }

        return account;
    }

    private Account seedDemoCompany() {
        Account account = accountRepository.findByEmailIgnoreCase(DEMO_COMPANY_EMAIL).orElse(null);
        if (account == null) {
            account = Account.builder()
                    .email(DEMO_COMPANY_EMAIL)
                    .passwordHash(passwordEncoder.encode(demoPassword))
                    .accountType(AccountType.COMPANY)
                    .roles(new LinkedHashSet<>(Set.of(AccountRole.ROLE_COMPANY)))
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            account = accountRepository.save(account);
            log.info("Seeded demo Company account: {}", DEMO_COMPANY_EMAIL);
        } else {
            account.setPasswordHash(passwordEncoder.encode(demoPassword));
            account.setEnabled(true);
            account.setEmailVerified(true);
            account = accountRepository.save(account);
        }

        Company company = companyRepository.findByAccount_Id(account.getId()).orElse(null);
        if (company == null) {
            company = Company.builder()
                    .account(account)
                    .companyName(DEMO_COMPANY_NAME)
                    .registrationNumber("U40108MH2026PTC999999")
                    .supportEmail(DEMO_COMPANY_EMAIL)
                    .supportPhone("1800-209-8282")
                    .gstNumber("27AAACT2026M1Z5")
                    .businessAddress("Bombay House, 24 Homi Mody Street, Mumbai, Maharashtra")
                    .website("https://tatapower.com/ev")
                    .contactName("Tata Demo Operations Lead")
                    .verificationStatus(VerificationStatus.VERIFIED)
                    .active(true)
                    .agentMode(CompanyAgentMode.ASK_BEFORE_ACTIONS)
                    .agentMaxPriceChangePercent(10.0)
                    .agentAutoDisableFaultyChargers(true)
                    .agentAutoCreateMaintenanceTickets(true)
                    .build();
            companyRepository.save(company);
        } else {
            company.setCompanyName(DEMO_COMPANY_NAME);
            company.setActive(true);
            company.setVerificationStatus(VerificationStatus.VERIFIED);
            companyRepository.save(company);
        }

        return account;
    }

    private record DemoVehicleSpec(
            String regSuffix,
            String makeAndModel,
            String batteryCapacity,
            String connectorType,
            Set<ConnectorType> supportedConnectors,
            double efficiencyWhPerKm,
            double maxAcChargePowerKw,
            double maxDcChargePowerKw,
            double chargingEfficiency,
            int batteryPercent,
            double remainingRangeKm,
            VehicleConnectionStatus connectionStatus,
            VehicleTelemetrySource telemetrySource,
            boolean bluetoothSupported,
            boolean btSimulatorEnabled,
            String bluetoothDeviceName
    ) {}

    private void seedDemoVehicles(Account driver, String prefix) {
        List<DemoVehicleSpec> specs = List.of(
            new DemoVehicleSpec("001", "Tata Nexon EV Long Range", "40.5 kWh", "CCS2",
                new LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)), 132.0, 7.2, 50.0, 0.90, 85, 260.0,
                VehicleConnectionStatus.CONNECTED, VehicleTelemetrySource.BLUETOOTH_DEMO, true, true, "Tata Nexon BT-DEMO"),
            new DemoVehicleSpec("002", "Mahindra BE 6", "79.0 kWh", "CCS2",
                new LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)), 160.0, 11.0, 175.0, 0.92, 88, 435.0,
                VehicleConnectionStatus.CONNECTED, VehicleTelemetrySource.BLUETOOTH_DEMO, true, true, "Mahindra BE6 BT-DEMO"),
            new DemoVehicleSpec("003", "Tata Curvv EV", "55.0 kWh", "CCS2",
                new LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)), 142.0, 7.2, 70.0, 0.90, 80, 310.0,
                VehicleConnectionStatus.CONNECTED, VehicleTelemetrySource.BLUETOOTH_DEMO, true, true, "Tata Curvv BT-DEMO"),
            new DemoVehicleSpec("004", "MG Windsor EV", "38.0 kWh", "CCS2",
                new LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)), 145.0, 7.4, 50.0, 0.90, 92, 240.0,
                VehicleConnectionStatus.CONNECTED, VehicleTelemetrySource.BLUETOOTH_DEMO, true, true, "MG Windsor BT-DEMO"),
            new DemoVehicleSpec("005", "Hyundai Creta Electric", "51.4 kWh", "CCS2",
                new LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)), 140.0, 11.0, 65.0, 0.90, 75, 275.0,
                VehicleConnectionStatus.CONNECTED, VehicleTelemetrySource.BLUETOOTH_DEMO, true, true, "Creta EV BT-DEMO"),
            new DemoVehicleSpec("006", "BMW iX1", "66.5 kWh", "CCS2",
                new LinkedHashSet<>(List.of(ConnectorType.CCS2, ConnectorType.TYPE2)), 170.0, 11.0, 130.0, 0.91, 85, 332.0,
                VehicleConnectionStatus.CONNECTED, VehicleTelemetrySource.BLUETOOTH_DEMO, true, true, "BMW iX1 BT-DEMO")
        );

        for (DemoVehicleSpec spec : specs) {
            String regNumber = prefix + spec.regSuffix();
            Vehicle vehicle = vehicleRepository.findByRegistrationNumber(regNumber).orElse(null);
            if (vehicle == null) {
                vehicle = Vehicle.builder()
                        .userId(driver.getId())
                        .makeAndModel(spec.makeAndModel())
                        .registrationNumber(regNumber)
                        .batteryCapacity(spec.batteryCapacity())
                        .connectorType(spec.connectorType())
                        .supportedConnectors(new LinkedHashSet<>(spec.supportedConnectors()))
                        .efficiencyWhPerKm(spec.efficiencyWhPerKm())
                        .maxAcChargePowerKw(spec.maxAcChargePowerKw())
                        .maxDcChargePowerKw(spec.maxDcChargePowerKw())
                        .chargingEfficiency(spec.chargingEfficiency())
                        .batteryPercent(spec.batteryPercent())
                        .remainingRangeKm(spec.remainingRangeKm())
                        .charging(false)
                        .connectionStatus(spec.connectionStatus())
                        .bluetoothSupported(spec.bluetoothSupported())
                        .btSimulatorEnabled(spec.btSimulatorEnabled())
                        .bluetoothDeviceName(spec.bluetoothDeviceName())
                        .telemetrySource(spec.telemetrySource())
                        .telemetryUpdatedAt(LocalDateTime.now())
                        .lastChargingStation("Noida Demo Charging Hub")
                        .lastChargedAt(LocalDateTime.now().minusHours(8))
                        .build();
                vehicleRepository.save(vehicle);
                log.info("Seeded EV: {} ({}) for user ID {}", spec.makeAndModel(), regNumber, driver.getId());
            } else {
                // Restore canonical EV state on restart
                vehicle.setUserId(driver.getId());
                vehicle.setMakeAndModel(spec.makeAndModel());
                vehicle.setBatteryCapacity(spec.batteryCapacity());
                vehicle.setConnectorType(spec.connectorType());
                vehicle.setSupportedConnectors(new LinkedHashSet<>(spec.supportedConnectors()));
                vehicle.setEfficiencyWhPerKm(spec.efficiencyWhPerKm());
                vehicle.setMaxAcChargePowerKw(spec.maxAcChargePowerKw());
                vehicle.setMaxDcChargePowerKw(spec.maxDcChargePowerKw());
                vehicle.setChargingEfficiency(spec.chargingEfficiency());
                vehicle.setBatteryPercent(spec.batteryPercent());
                vehicle.setRemainingRangeKm(spec.remainingRangeKm());
                vehicle.setCharging(false);
                vehicle.setConnectionStatus(spec.connectionStatus());
                vehicle.setBluetoothSupported(spec.bluetoothSupported());
                vehicle.setBtSimulatorEnabled(spec.btSimulatorEnabled());
                vehicle.setBluetoothDeviceName(spec.bluetoothDeviceName());
                vehicle.setTelemetrySource(spec.telemetrySource());
                vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);
                log.info("Restored canonical EV: {} ({}) for user ID {}", spec.makeAndModel(), regNumber, driver.getId());
            }
        }
    }

    private List<LandListing> seedDemoHostProperties(Account host) {
        List<LandListing> list = new ArrayList<>();

        list.add(upsertProperty(host.getId(), "Noida Commercial EV Hub",
                "Sector 62, Electronic City", "Noida", "Uttar Pradesh", "201301",
                28.6280, 77.3750, PropertyType.COMMERCIAL_PARKING, 4, 150.0, 96));

        list.add(upsertProperty(host.getId(), "Agra Highway Expressway Hub",
                "NH-19 Expressway Junction, Fatehabad Road", "Agra", "Uttar Pradesh", "282001",
                27.1767, 78.0081, PropertyType.HIGHWAY, 6, 250.0, 98));

        list.add(upsertProperty(host.getId(), "Jhansi Bypass Travel Plaza",
                "NH-44 Bypass Junction, Shivpuri Road", "Jhansi", "Uttar Pradesh", "284001",
                25.4484, 78.5685, PropertyType.HIGHWAY, 4, 200.0, 94));

        return list;
    }

    private LandListing upsertProperty(Long hostId, String title, String address, String city, String state,
                                       String pincode, double lat, double lng, PropertyType type,
                                       int parkingBays, double loadKw, int score) {
        LandListing property = landListingRepository.findByHostUserIdAndTitle(hostId, title).orElse(null);
        if (property == null) {
            property = LandListing.builder()
                    .hostUserId(hostId)
                    .title(title)
                    .address(address)
                    .city(city)
                    .state(state)
                    .pincode(pincode)
                    .latitude(lat)
                    .longitude(lng)
                    .connectorType("CCS2")
                    .powerKw(150.0)
                    .pricePerKwh(13.0)
                    .propertyType(type)
                    .availableParkingBays(parkingBays)
                    .powerPhase(PowerPhase.THREE_PHASE)
                    .availableLoadKw(loadKw)
                    .operatingHours("Open 24 hours")
                    .ownershipType(OwnershipType.OWNED)
                    .discoverable(true)
                    .status(LandListingStatus.APPROVED)
                    .verificationStage("VERIFIED")
                    .videoVerified(true)
                    .propertyScore(score)
                    .build();
            return landListingRepository.save(property);
        }
        property.setAddress(address);
        property.setCity(city);
        property.setState(state);
        property.setPincode(pincode);
        property.setLatitude(lat);
        property.setLongitude(lng);
        property.setDiscoverable(true);
        property.setStatus(LandListingStatus.APPROVED);
        property.setVerificationStage("VERIFIED");
        property.setPropertyScore(score);
        return landListingRepository.save(property);
    }

    private void seedDemoChargingNetwork(Company company, Account host, List<LandListing> hostProperties) {
        LandListing agraProp = hostProperties.stream()
                .filter(p -> p.getTitle().contains("Agra"))
                .findFirst().orElse(null);
        LandListing jhansiProp = hostProperties.stream()
                .filter(p -> p.getTitle().contains("Jhansi"))
                .findFirst().orElse(null);

        // 1. Noida Demo Charging Hub (COMPANY_OWNED)
        upsertStation(
                "NOIDA_DEMO_01",
                "Noida Demo Charging Hub",
                "Noida Expressway, Sector 128, Noida",
                "Noida",
                28.5355, 77.3910,
                13.0, 4.8, 142,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-NOIDA-CCS2-01", ConnectorType.CCS2, 180.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-NOIDA-CCS2-02", ConnectorType.CCS2, 120.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-NOIDA-TYPE2-01", ConnectorType.TYPE2, 22.0, ChargerStatus.ONLINE, true)
                )
        );

        // 2. Mathura Demo Charging Hub (COMPANY_OWNED)
        upsertStation(
                "MATHURA_DEMO_01",
                "Mathura Demo Charging Hub",
                "Yamuna Expressway Milestone 110, Mathura",
                "Mathura",
                27.4924, 77.6737,
                12.6, 4.7, 98,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-MATHURA-CCS2-01", ConnectorType.CCS2, 180.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-MATHURA-CCS2-02", ConnectorType.CCS2, 120.0, ChargerStatus.ONLINE, true)
                )
        );

        // 3. Agra Demo Charging Hub (HOST_PARTNERED - Host property operated by Tata Company)
        // Contains REROUTE_DEMO_TARGET_CHARGER_CODE: DEMO-AGRA-CCS2-01
        upsertStation(
                "AGRA_DEMO_01",
                "Agra Demo Charging Hub",
                "NH-19 Highway Travel Plaza, Agra",
                "Agra",
                27.1767, 78.0081,
                12.8, 4.8, 186,
                StationOwnershipType.HOST_PARTNERED,
                company.getId(), company.getCompanyName(),
                host.getId(), "Vidyut Demo Host", agraProp != null ? agraProp.getId() : null,
                List.of(
                        new ConnectorSeed(REROUTE_DEMO_TARGET_CHARGER_CODE, ConnectorType.CCS2, 180.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-AGRA-CCS2-02", ConnectorType.CCS2, 120.0, ChargerStatus.ONLINE, true)
                )
        );

        // 4. Gwalior Demo Charging Hub (COMPANY_OWNED)
        upsertStation(
                "GWALIOR_DEMO_01",
                "Gwalior Demo Charging Hub",
                "NH-44 Bypass, Gwalior",
                "Gwalior",
                26.2183, 78.1828,
                12.3, 4.6, 85,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-GWALIOR-CCS2-01", ConnectorType.CCS2, 150.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-GWALIOR-CCS2-02", ConnectorType.CCS2, 120.0, ChargerStatus.ONLINE, true)
                )
        );

        // 5. Jhansi Demo Charging Hub (HOST_PARTNERED - Host property operated by Tata Company)
        upsertStation(
                "JHANSI_DEMO_01",
                "Jhansi Demo Charging Hub",
                "NH-44 Highway Gateway, Jhansi",
                "Jhansi",
                25.4484, 78.5685,
                12.2, 4.7, 74,
                StationOwnershipType.HOST_PARTNERED,
                company.getId(), company.getCompanyName(),
                host.getId(), "Vidyut Demo Host", jhansiProp != null ? jhansiProp.getId() : null,
                List.of(
                        new ConnectorSeed("DEMO-JHANSI-CCS2-01", ConnectorType.CCS2, 150.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-JHANSI-CCS2-02", ConnectorType.CCS2, 120.0, ChargerStatus.ONLINE, true)
                )
        );

        // 6. Lalitpur Highway Demo Charger (COMPANY_OWNED corridor backup)
        upsertStation(
                "LALITPUR_DEMO_01",
                "Lalitpur Highway Demo Charger",
                "NH-44 Highway Mile 220, Lalitpur",
                "Lalitpur",
                24.6905, 78.4189,
                12.0, 4.5, 48,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-LALITPUR-CCS2-01", ConnectorType.CCS2, 150.0, ChargerStatus.ONLINE, true)
                )
        );

        // 7. Bina Junction Demo Charger (COMPANY_OWNED corridor intermediate)
        upsertStation(
                "BIN_DEMO_01",
                "Bina Junction Demo Charger",
                "Bina Bypass, Bina",
                "Bina",
                24.1717, 78.1780,
                11.9, 4.6, 52,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-BINA-CCS2-01", ConnectorType.CCS2, 150.0, ChargerStatus.ONLINE, true)
                )
        );

        // 8. Vidisha Demo Charging Hub (COMPANY_OWNED corridor intermediate)
        upsertStation(
                "VDH_DEMO_01",
                "Vidisha Demo Charging Hub",
                "Bhopal-Vidisha Highway, Vidisha",
                "Vidisha",
                23.5236, 77.8061,
                12.1, 4.7, 64,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-VIDISHA-CCS2-01", ConnectorType.CCS2, 150.0, ChargerStatus.ONLINE, true)
                )
        );

        // 7. Bhopal Demo Charging Hub (COMPANY_OWNED)
        upsertStation(
                "BHOPAL_DEMO_01",
                "Bhopal Demo Charging Hub",
                "Bhopal Bypass Road, Bhopal",
                "Bhopal",
                23.2599, 77.4126,
                12.4, 4.9, 210,
                StationOwnershipType.COMPANY_OWNED,
                company.getId(), company.getCompanyName(),
                null, null, null,
                List.of(
                        new ConnectorSeed("DEMO-BHOPAL-CCS2-01", ConnectorType.CCS2, 180.0, ChargerStatus.ONLINE, true),
                        new ConnectorSeed("DEMO-BHOPAL-CCS2-02", ConnectorType.CCS2, 120.0, ChargerStatus.ONLINE, true)
                )
        );
    }

    private void upsertStation(String seedKey, String name, String address, String city,
                               double lat, double lng, double price, double rating, int reviews,
                               StationOwnershipType ownershipType,
                               Long operatorCompanyId, String operatorCompanyName,
                               Long propertyOwnerAccountId, String propertyOwnerName, Long hostPartnershipId,
                               List<ConnectorSeed> connectorSeeds) {
        ChargingStation station = stationRepository.findByDemoSeedKey(seedKey)
                .or(() -> stationRepository.findByName(name))
                .orElse(null);

        if (station == null) {
            station = ChargingStation.builder()
                    .demoSeedKey(seedKey)
                    .name(name)
                    .address(address)
                    .city(city)
                    .latitude(lat)
                    .longitude(lng)
                    .pricePerKwh(price)
                    .rating(rating)
                    .reviewCount(reviews)
                    .amenities("Cafe, Restroom, High-Speed Wi-Fi, 24x7 Security, Canopy")
                    .workingHours("Open 24 hours")
                    .status(StationStatus.ACTIVE)
                    .availability(StationAvailability.AVAILABLE)
                    .queueCount(0)
                    .occupancyPercent(15)
                    .demoData(true)
                    .ownershipType(ownershipType)
                    .operatorCompanyId(operatorCompanyId)
                    .operatorCompanyName(operatorCompanyName)
                    .propertyOwnerAccountId(propertyOwnerAccountId)
                    .propertyOwnerName(propertyOwnerName)
                    .hostUserId(propertyOwnerAccountId)
                    .hostPartnershipId(hostPartnershipId)
                    .operatingModel(ownershipType == StationOwnershipType.HOST_PARTNERED
                            ? "HOST_OWNED_COMPANY_OPERATED" : "COMPANY_OWNED_AND_OPERATED")
                    .connectors(new ArrayList<>())
                    .build();
            station = stationRepository.save(station);
            log.info("Seeded demo station: {} ({})", name, seedKey);
        } else {
            // Restore canonical station state
            station.setDemoSeedKey(seedKey);
            station.setName(name);
            station.setAddress(address);
            station.setCity(city);
            station.setLatitude(lat);
            station.setLongitude(lng);
            station.setPricePerKwh(price);
            station.setRating(rating);
            station.setReviewCount(reviews);
            station.setAmenities("Cafe, Restroom, High-Speed Wi-Fi, 24x7 Security, Canopy");
            station.setWorkingHours("Open 24 hours");
            station.setQueueCount(0);
            station.setOccupancyPercent(15);
            station.setDemoData(true);
            station.setOwnershipType(ownershipType);
            station.setOperatorCompanyId(operatorCompanyId);
            station.setOperatorCompanyName(operatorCompanyName);
            station.setPropertyOwnerAccountId(propertyOwnerAccountId);
            station.setPropertyOwnerName(propertyOwnerName);
            station.setHostUserId(propertyOwnerAccountId);
            station.setHostPartnershipId(hostPartnershipId);
            station.setStatus(StationStatus.ACTIVE);
            station.setAvailability(StationAvailability.AVAILABLE);
            station.setEmergencyDisabled(false);
            station = stationRepository.save(station);
        }

        syncConnectors(station, connectorSeeds);
    }

    private void syncConnectors(ChargingStation station, List<ConnectorSeed> connectorSeeds) {
        if (station.getConnectors() == null) {
            station.setConnectors(new ArrayList<>());
        }

        for (ConnectorSeed seed : connectorSeeds) {
            ChargingConnector connector = connectorRepository.findByChargerCode(seed.code()).orElse(null);
            if (connector == null) {
                connector = ChargingConnector.builder()
                        .station(station)
                        .chargerCode(seed.code())
                        .type(seed.type())
                        .powerKw(seed.powerKw())
                        .status(seed.status())
                        .available(seed.available())
                        .healthScore(seed.status() == ChargerStatus.FAULT ? 38
                                : seed.status() == ChargerStatus.MAINTENANCE ? 60
                                : seed.status() == ChargerStatus.CHARGING ? 94 : 98)
                        .firmwareVersion("4.2.0-demo")
                        .maintenanceMode(seed.status() == ChargerStatus.MAINTENANCE)
                        .lastHeartbeat(LocalDateTime.now())
                        .faultCode(seed.status() == ChargerStatus.FAULT ? "SYNTHETIC_DEMO_FAULT" : null)
                        .build();
                station.getConnectors().add(connector);
                connectorRepository.save(connector);
            } else {
                // Restorative behavior: reset target charger and other connectors back to healthy canonical state
                connector.setStation(station);
                connector.setType(seed.type());
                connector.setPowerKw(seed.powerKw());
                connector.setStatus(seed.status());
                connector.setAvailable(seed.available());
                connector.setHealthScore(seed.status() == ChargerStatus.FAULT ? 38
                        : seed.status() == ChargerStatus.MAINTENANCE ? 60
                        : seed.status() == ChargerStatus.CHARGING ? 94 : 98);
                connector.setMaintenanceMode(seed.status() == ChargerStatus.MAINTENANCE);
                connector.setFaultCode(seed.status() == ChargerStatus.FAULT
                        ? "SYNTHETIC_DEMO_FAULT" : null);
                connector.setFaultReason(seed.status() == ChargerStatus.FAULT ? "Seeded synthetic demo fault" : null);
                connector.setStatusSource("DEMO_SEED_RESET");
                connector.setStatusChangedAt(LocalDateTime.now());
                connector.setLastHeartbeat(LocalDateTime.now());
                connectorRepository.save(connector);
            }
        }
    }

    private void seedDemoBookings(Account driver) {
        ChargingStation station = stationRepository.findByDemoSeedKey("NOIDA_DEMO_01").orElse(null);
        if (station == null) return;

        String idempotencyKey = "DEMO-BOOKING-INIT-01";
        if (bookingRepository.findAll().stream().noneMatch(b -> idempotencyKey.equals(b.getIdempotencyKey()))) {
            Booking booking = Booking.builder()
                    .userId(driver.getId())
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .stationAddress(station.getAddress())
                    .startTime(LocalDateTime.now().minusDays(1).withHour(14).withMinute(0))
                    .endTime(LocalDateTime.now().minusDays(1).withHour(14).withMinute(45))
                    .durationHours(1)
                    .durationMinutes(45)
                    .totalAmount(450.0)
                    .kwhDelivered(32.5)
                    .status(BookingStatus.COMPLETED)
                    .idempotencyKey(idempotencyKey)
                    .build();
            bookingRepository.save(booking);
        }
    }

    private void seedDemoNotifications(Account driver, Account host) {
        if (notificationRepository.findByUserIdOrderByTimestampDesc(driver.getId()).isEmpty()) {
            notificationRepository.save(Notification.builder()
                    .userId(driver.getId())
                    .title("Welcome to Vidyut Autopilot")
                    .message("Your Tata Nexon EV (DEMO-EV-001) is synced. Autopilot AI route planning is ready for Delhi → Bhopal.")
                    .type(NotificationType.SYSTEM_ALERT)
                    .isRead(false)
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        if (notificationRepository.findByUserIdOrderByTimestampDesc(host.getId()).isEmpty()) {
            notificationRepository.save(Notification.builder()
                    .userId(host.getId())
                    .title("Host Property Verified")
                    .message("Agra Highway Expressway Hub has been verified and commissioned with Tata EV Charging Demo.")
                    .type(NotificationType.AGENT_REPLAN)
                    .isRead(false)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

    private void seedDemoMaintenanceTickets(Company company) {
        ChargingConnector connector = connectorRepository.findByChargerCode("DEMO-AGRA-CCS2-02").orElse(null);
        ChargingStation station = stationRepository.findByDemoSeedKey("AGRA_DEMO_01").orElse(null);
        if (connector == null || station == null) return;

        if (maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(company.getId()).isEmpty()) {
            maintenanceTicketRepository.save(CompanyMaintenanceTicket.builder()
                    .companyId(company.getId())
                    .chargerId(connector.getId())
                    .chargerCode(connector.getChargerCode())
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .city(station.getCity())
                    .priority(MaintenancePriority.MEDIUM)
                    .status(MaintenanceTicketStatus.RESOLVED)
                    .issue("Scheduled preventative firmware validation and cable insulation test.")
                    .assignedTo("Rajesh Sharma (Field Technician)")
                    .resolutionNote("All diagnostics verified. Connector operating at peak efficiency.")
                    .resolvedAt(LocalDateTime.now().minusDays(2))
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .updatedAt(LocalDateTime.now().minusDays(2))
                    .build());
        }
    }

    private record ConnectorSeed(String code, ConnectorType type, double powerKw, ChargerStatus status, boolean available) {}

    // ─── Nationwide Demo Network ──────────────────────────────────────────────

    /** JSON shape of demo/chargers-india.json */
    private record HubSeed(
            String key,
            String name,
            String address,
            String city,
            double latitude,
            double longitude,
            double pricePerKwh,
            double powerKw
    ) {}

    /** JSON shape of demo/district-chargers-india.json */
    private record DistrictSeed(
            String key,
            String state,
            String district,
            double latitude,
            double longitude
    ) {}

    /** Highway dataset keys represented by the protected nine-station Delhi-Bhopal corridor. */
    private static final Set<String> CANONICAL_HIGHWAY_KEYS = Set.of(
            "NOI", "MTR", "AGR", "GWL", "JHS", "LTP", "BIN", "VDH", "BHO"
    );

    private enum StationArchetype {
        HIGHWAY_FAST,
        CITY_FAST,
        DESTINATION,
        SMALL_DISTRICT,
        LEGACY_COMPATIBILITY
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    void seedNationwideHighwayHubs(Company company) throws IOException {
        List<HubSeed> seeds;
        try (var in = new ClassPathResource("demo/chargers-india.json").getInputStream()) {
            seeds = objectMapper.readValue(in, new TypeReference<>() {});
        }
        int restored = 0, skipped = 0;
        for (HubSeed seed : seeds) {
            if (CANONICAL_HIGHWAY_KEYS.contains(seed.key())) {
                skipped++;
                continue;
            }
            double primaryPower = seed.powerKw() > 0 ? seed.powerKw() : 180.0;
            ChargingStation station = upsertNationwideStation(
                    seed.key(), seed.name(), seed.address() != null ? seed.address() : seed.city() + " Highway Hub",
                    seed.city(), seed.latitude(), seed.longitude(),
                    seed.pricePerKwh() > 0 ? seed.pricePerKwh() : 13.0,
                    company, StationAvailability.AVAILABLE, 20, 0);
            syncConnectors(station, List.of(
                    new ConnectorSeed("HUB-" + seed.key() + "-CCS2-01", ConnectorType.CCS2,
                            Math.max(120.0, primaryPower), ChargerStatus.ONLINE, true),
                    new ConnectorSeed("HUB-" + seed.key() + "-CCS2-02", ConnectorType.CCS2,
                            150.0, ChargerStatus.ONLINE, true),
                    new ConnectorSeed("HUB-" + seed.key() + "-CCS2-03", ConnectorType.CCS2,
                            120.0, ChargerStatus.ONLINE, true),
                    new ConnectorSeed("HUB-" + seed.key() + "-TYPE2-01", ConnectorType.TYPE2,
                            22.0, ChargerStatus.ONLINE, true)));
            restored++;
        }
        log.info("🛣  Nationwide highway hubs restored: {}, protected canonical hubs skipped: {}.", restored, skipped);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    void seedDistrictHubs(Company company) throws IOException {
        List<DistrictSeed> seeds;
        try (var in = new ClassPathResource("demo/district-chargers-india.json").getInputStream()) {
            seeds = objectMapper.readValue(in, new TypeReference<>() {});
        }
        int restored = 0;
        for (DistrictSeed seed : seeds) {
            int stableHash = seed.key().hashCode();
            StationArchetype archetype = StationArchetype.values()[Math.floorMod(stableHash, StationArchetype.values().length)];
            int statusVariant = Math.floorMod(stableHash * 31, 100);
            StationAvailability availability = statusVariant < 80 ? StationAvailability.AVAILABLE
                    : statusVariant < 90 ? StationAvailability.CHARGING : StationAvailability.UNAVAILABLE;
            int occupancy = statusVariant < 80 ? 20 + Math.floorMod(stableHash, 36)
                    : statusVariant < 90 ? 85 + Math.floorMod(stableHash, 11) : 0;
            int queue = statusVariant >= 80 && statusVariant < 90 ? 2 + Math.floorMod(stableHash, 4) : 0;
            ChargingStation station = upsertNationwideStation(
                    seed.key(), "Vidyut " + seed.district() + " District Demo Hub",
                    "Synthetic district coverage point, " + seed.district() + ", " + seed.state(),
                    seed.district(), seed.latitude(), seed.longitude(),
                    11.5 + Math.floorMod(stableHash, 5) * 0.6,
                    company, availability, occupancy, queue);
            syncConnectors(station, districtConnectorSeeds(seed.key(), archetype, statusVariant));
            restored++;
        }
        log.info("🗺  District demo hubs restored: {} across the versioned dataset.", restored);
    }

    private ChargingStation upsertNationwideStation(String seedKey, String name, String address, String city,
                                                     double latitude, double longitude, double pricePerKwh,
                                                     Company company, StationAvailability availability,
                                                     int occupancyPercent, int queueCount) {
        ChargingStation station = stationRepository.findByDemoSeedKey(seedKey).orElse(null);
        if (station == null) {
            station = ChargingStation.builder().connectors(new ArrayList<>()).build();
        }
        int variant = Math.floorMod(seedKey.hashCode(), 5);
        station.setDemoSeedKey(seedKey);
        station.setName(name);
        station.setAddress(address);
        station.setCity(city);
        station.setLatitude(latitude);
        station.setLongitude(longitude);
        station.setPricePerKwh(pricePerKwh);
        station.setRating(4.3 + (variant % 4) * 0.1);
        station.setReviewCount(40 + variant * 19);
        station.setAmenities("Washroom, Food, Wi-Fi, 24x7 Security");
        station.setWorkingHours("Open 24 hours");
        station.setQueueCount(queueCount);
        station.setOccupancyPercent(occupancyPercent);
        station.setStatus(StationStatus.ACTIVE);
        station.setAvailability(availability);
        station.setBookingSlotMinutes(30);
        station.setDemoData(true);
        station.setOwnershipType(StationOwnershipType.COMPANY_OWNED);
        station.setPropertyOwnerAccountId(company.getAccount().getId());
        station.setPropertyOwnerName(company.getCompanyName());
        station.setOperatorCompanyId(company.getId());
        station.setOperatorCompanyName(company.getCompanyName());
        station.setEquipmentOwnerName(company.getCompanyName());
        station.setOperatingModel("COMPANY_OWNED_AND_OPERATED");
        if (station.getConnectors() == null) {
            station.setConnectors(new ArrayList<>());
        }
        return stationRepository.save(station);
    }

    private List<ConnectorSeed> districtConnectorSeeds(String seedKey, StationArchetype archetype, int statusVariant) {
        List<ConnectorSeed> healthy = switch (archetype) {
            case HIGHWAY_FAST -> List.of(
                    connector(seedKey, "CCS2", 1, ConnectorType.CCS2, 180),
                    connector(seedKey, "CCS2", 2, ConnectorType.CCS2, 150),
                    connector(seedKey, "CCS2", 3, ConnectorType.CCS2, 120),
                    connector(seedKey, "TYPE2", 1, ConnectorType.TYPE2, 22));
            case CITY_FAST -> List.of(
                    connector(seedKey, "CCS2", 1, ConnectorType.CCS2, 120),
                    connector(seedKey, "CCS2", 2, ConnectorType.CCS2, 60),
                    connector(seedKey, "TYPE2", 1, ConnectorType.TYPE2, 22));
            case DESTINATION -> List.of(
                    connector(seedKey, "TYPE2", 1, ConnectorType.TYPE2, 22),
                    connector(seedKey, "TYPE2", 2, ConnectorType.TYPE2, 11),
                    connector(seedKey, "CCS2", 1, ConnectorType.CCS2, 60));
            case SMALL_DISTRICT -> List.of(
                    connector(seedKey, "CCS2", 1, ConnectorType.CCS2, 60),
                    connector(seedKey, "TYPE2", 1, ConnectorType.TYPE2, 22));
            case LEGACY_COMPATIBILITY -> List.of(
                    connector(seedKey, "CCS2", 1, ConnectorType.CCS2, 60),
                    connector(seedKey, "TYPE2", 1, ConnectorType.TYPE2, 22),
                    connector(seedKey, "TYPE1", 1, ConnectorType.TYPE1, 7.4));
        };

        if (statusVariant < 80) {
            return healthy;
        }
        List<ConnectorSeed> operationalState = new ArrayList<>();
        for (int index = 0; index < healthy.size(); index++) {
            ConnectorSeed seed = healthy.get(index);
            if (statusVariant < 90) {
                boolean occupied = index == 0;
                operationalState.add(new ConnectorSeed(seed.code(), seed.type(), seed.powerKw(),
                        occupied ? ChargerStatus.CHARGING : ChargerStatus.ONLINE, !occupied));
            } else {
                ChargerStatus status = statusVariant < 95 ? ChargerStatus.MAINTENANCE : ChargerStatus.FAULT;
                operationalState.add(new ConnectorSeed(seed.code(), seed.type(), seed.powerKw(), status, false));
            }
        }
        return operationalState;
    }

    private ConnectorSeed connector(String seedKey, String label, int sequence, ConnectorType type, double powerKw) {
        return new ConnectorSeed("DIST-" + seedKey + "-" + label + "-" + String.format("%02d", sequence),
                type, powerKw, ChargerStatus.ONLINE, true);
    }
}
