package com.vidyut.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.company.entity.Company;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.notification.repository.NotificationRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.repository.EvWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class DemoDataSeederTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private EvUserProfileRepository evUserProfileRepository;
    @Mock
    private HostProfileRepository hostProfileRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private EvWalletRepository walletRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private ChargingStationRepository stationRepository;
    @Mock
    private ChargingConnectorRepository connectorRepository;
    @Mock
    private LandListingRepository landListingRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CompanyMaintenanceTicketRepository maintenanceTicketRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(seeder, "enabled", true);
        ReflectionTestUtils.setField(seeder, "demoPassword", "VidyutDemo@2026");
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        lenient().when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(seeder, "enabled", false);
        seeder.run();
        verifyNoInteractions(accountRepository);
    }

    @Test
    void runsIdempotentlyAndRestoresState() {
        Account driver = Account.builder().id(1L).email(DemoDataSeeder.DEMO_DRIVER_EMAIL)
                .accountType(AccountType.INDIVIDUAL).roles(Set.of(AccountRole.ROLE_EV_USER)).build();
        Account host = Account.builder().id(2L).email(DemoDataSeeder.DEMO_HOST_EMAIL)
                .accountType(AccountType.INDIVIDUAL).roles(Set.of(AccountRole.ROLE_HOST)).build();
        Account companyAcc = Account.builder().id(3L).email(DemoDataSeeder.DEMO_COMPANY_EMAIL)
                .accountType(AccountType.COMPANY).roles(Set.of(AccountRole.ROLE_COMPANY)).build();
        Company company = Company.builder().id(10L).account(companyAcc).companyName(DemoDataSeeder.DEMO_COMPANY_NAME).build();

        when(accountRepository.findByEmailIgnoreCase(DemoDataSeeder.DEMO_DRIVER_EMAIL)).thenReturn(Optional.of(driver));
        when(accountRepository.findByEmailIgnoreCase(DemoDataSeeder.DEMO_HOST_EMAIL)).thenReturn(Optional.of(host));
        when(accountRepository.findByEmailIgnoreCase(DemoDataSeeder.DEMO_COMPANY_EMAIL)).thenReturn(Optional.of(companyAcc));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        when(companyRepository.findByAccount_Id(3L)).thenReturn(Optional.of(company));
        when(evUserProfileRepository.findById(1L)).thenReturn(Optional.of(EvUserProfile.builder().accountId(1L).build()));
        when(hostProfileRepository.findById(2L)).thenReturn(Optional.of(HostProfile.builder().accountId(2L).build()));

        Vehicle mockEv = Vehicle.builder().id(100L).registrationNumber(DemoDataSeeder.DEMO_EV_REGISTRATION).batteryPercent(40).build();
        when(vehicleRepository.findByRegistrationNumber(DemoDataSeeder.DEMO_EV_REGISTRATION)).thenReturn(Optional.of(mockEv));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        when(landListingRepository.findByHostUserIdAndTitle(anyLong(), anyString())).thenReturn(Optional.empty());
        when(landListingRepository.save(any(LandListing.class))).thenAnswer(i -> {
            LandListing l = i.getArgument(0);
            l.setId(200L);
            return l;
        });

        // Simulate Agra station having the target charger previously set to FAULT
        ChargingStation agraStation = ChargingStation.builder()
                .id(50L)
                .demoSeedKey("AGRA_DEMO_01")
                .name("Agra Demo Charging Hub")
                .ownershipType(StationOwnershipType.HOST_PARTNERED)
                .connectors(new ArrayList<>())
                .build();
        ChargingConnector faultyTargetConnector = ChargingConnector.builder()
                .id(501L)
                .chargerCode(DemoDataSeeder.REROUTE_DEMO_TARGET_CHARGER_CODE)
                .status(ChargerStatus.FAULT)
                .available(false)
                .build();
        when(stationRepository.findByDemoSeedKey(anyString())).thenReturn(Optional.of(agraStation));
        when(stationRepository.save(any(ChargingStation.class))).thenAnswer(i -> i.getArgument(0));
        when(connectorRepository.findByChargerCode(DemoDataSeeder.REROUTE_DEMO_TARGET_CHARGER_CODE)).thenReturn(Optional.of(faultyTargetConnector));
        when(connectorRepository.findByChargerCode(anyString())).thenReturn(Optional.of(faultyTargetConnector));

        when(bookingRepository.findAll()).thenReturn(Collections.emptyList());
        when(notificationRepository.findByUserIdOrderByTimestampDesc(anyLong())).thenReturn(Collections.emptyList());
        when(maintenanceTicketRepository.findByCompanyIdOrderByUpdatedAtDesc(anyLong())).thenReturn(Collections.emptyList());

        // Run the restorative seeder
        seeder.run();

        // 1. Verify EV restored to 85% battery
        ArgumentCaptor<Vehicle> evCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository, atLeastOnce()).save(evCaptor.capture());
        Vehicle restoredEv = evCaptor.getAllValues().stream()
                .filter(vehicle -> DemoDataSeeder.DEMO_EV_REGISTRATION.equals(vehicle.getRegistrationNumber()))
                .findFirst().orElseThrow();
        assertThat(restoredEv.getBatteryPercent()).isEqualTo(85);
        assertThat(restoredEv.getRemainingRangeKm()).isEqualTo(260.0);

        // 2. Verify target connector restored to ONLINE & available
        ArgumentCaptor<ChargingConnector> connCaptor = ArgumentCaptor.forClass(ChargingConnector.class);
        verify(connectorRepository, atLeastOnce()).save(connCaptor.capture());
        ChargingConnector restoredTarget = connCaptor.getAllValues().stream()
                .filter(c -> DemoDataSeeder.REROUTE_DEMO_TARGET_CHARGER_CODE.equals(c.getChargerCode()))
                .findFirst().orElse(null);
        assertThat(restoredTarget).isNotNull();
        assertThat(restoredTarget.getStatus()).isEqualTo(ChargerStatus.ONLINE);
        assertThat(restoredTarget.isAvailable()).isTrue();
    }
}
