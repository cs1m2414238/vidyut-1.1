package com.vidyut.company.service;

import com.vidyut.admin.service.AdminControlService;
import com.vidyut.admin.service.OperationalControlService;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.company.dto.*;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.CompanyAgentMode;
import com.vidyut.company.repository.*;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.payment.repository.PayoutRepository;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.service.ChargingStationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyOperationsServiceTest {
    @Mock CompanyOperatorContextService operatorContextService;
    @Mock CompanyRepository companyRepository;
    @Mock CompanyEmployeeRepository employeeRepository;
    @Mock CompanyActivityLogRepository activityLogRepository;
    @Mock CompanyMaintenanceTicketRepository maintenanceTicketRepository;
    @Mock ChargingStationRepository stationRepository;
    @Mock ChargingConnectorRepository connectorRepository;
    @Mock ChargingStationService stationService;
    @Mock ChargingSessionRepository sessionRepository;
    @Mock BookingRepository bookingRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PayoutRepository payoutRepository;
    @Mock LandListingRepository landListingRepository;
    @Mock CompanyVerificationService verificationService;
    @Mock AutopilotService autopilotService;
    @Mock AdminControlService adminControlService;
    @Mock OperationalControlService operationalControlService;
    @InjectMocks CompanyOperationsService service;

    @Test
    void recommendOnlyModeNeverExecutesThePreparedCompanyAction() {
        Company company = Company.builder().id(3L).agentMode(CompanyAgentMode.RECOMMEND_ONLY).build();
        when(verificationService.requireMarketplaceVerified(7L)).thenReturn(company);

        CompanyAgentActionResponse response = service.executeAgentAction(7L,
                new CompanyAgentActionRequest(CompanyAgentActionType.NOTIFY_STATION_MANAGER,
                        21L, null, null, null, "Review the fault", true));

        assertThat(response.state()).isEqualTo("RECOMMENDED_ONLY");
        assertThat(response.executedAt()).isNull();
        verifyNoInteractions(connectorRepository, autopilotService);
    }

    @Test
    void faultQuestionReturnsLiveImpactBackupsAndApprovalReadyActions() {
        Company company = Company.builder().id(3L).agentMode(CompanyAgentMode.ASK_BEFORE_ACTIONS).build();
        ChargingStation station = ChargingStation.builder().id(9L).name("Kanpur Central").address("Kanpur")
                .latitude(26.45).longitude(80.33).pricePerKwh(18).occupancyPercent(91)
                .status(StationStatus.ACTIVE).operatorCompanyId(3L).connectors(new ArrayList<>()).build();
        ChargingConnector faulty = ChargingConnector.builder().id(21L).station(station).chargerCode("KNP-03")
                .type(ConnectorType.CCS2).powerKw(120).status(ChargerStatus.FAULT).available(false)
                .healthScore(42).faultCode("Cooling temperature high").build();
        ChargingConnector backup = ChargingConnector.builder().id(22L).station(station).chargerCode("KNP-01")
                .type(ConnectorType.CCS2).powerKw(120).status(ChargerStatus.ONLINE).available(true).healthScore(98).build();
        station.getConnectors().addAll(List.of(faulty, backup));
        Booking affected = Booking.builder().id(51L).stationId(9L).stationName("Kanpur Central")
                .status(BookingStatus.CONFIRMED).startTime(LocalDateTime.now().plusHours(1)).totalAmount(950).build();

        when(companyRepository.findByAccount_Id(7L)).thenReturn(Optional.of(company));
        when(stationRepository.findByOperatorCompanyId(3L)).thenReturn(List.of(station));
        when(stationRepository.findByHostUserId(7L)).thenReturn(List.of());
        when(stationRepository.findBySupplierCompanyId(3L)).thenReturn(List.of());
        when(stationRepository.findAll()).thenReturn(List.of(station));
        when(bookingRepository.findByStationIdInOrderByStartTimeDesc(List.of(9L))).thenReturn(List.of(affected));
        when(paymentRepository.findByBookingIdIn(List.of(51L))).thenReturn(List.of());
        when(sessionRepository.findByStationIdInAndStatusOrderByStartedAtDesc(any(), any())).thenReturn(List.of());
        when(landListingRepository.findByDiscoverableTrueAndStatusIn(any())).thenReturn(List.of());

        when(operatorContextService.inspect(any(), any(), any(), any())).thenReturn(Map.of("maintenancePriorities", List.of(Map.of("stationId", 9L, "stationName", "Kanpur Central", "ownershipType", "COMPANY_OWNED", "issueCount", 1, "unavailableConnectors", 1, "affectedJourneyIds", List.of(), "activeBookingIds", List.of(51L)))));
        CompanyAgentResponse response = service.askAssistant(7L, "Explain the highest-priority charger fault");

        assertThat(response.intent()).isEqualTo("FAULT");
        assertThat(response.network().faults()).isEqualTo(1);
        assertThat(response.fault().chargerCode()).isEqualTo("KNP-03");
        assertThat(response.fault().affectedBookings()).isEqualTo(1);
        assertThat(response.fault().estimatedRevenueAtRisk()).isEqualTo(950);
        assertThat(response.fault().compatibleBackups()).contains("KNP-01");
        assertThat(response.actions()).extracting(CompanyAgentResponse.RecommendedAction::action)
                .containsExactly(CompanyAgentActionType.DISABLE_NEW_BOOKINGS,
                        CompanyAgentActionType.CREATE_MAINTENANCE_TICKET,
                        CompanyAgentActionType.NOTIFY_STATION_MANAGER);
        assertThat(response.actions()).allMatch(CompanyAgentResponse.RecommendedAction::requiresApproval);
    }

    @Test
    void approvedActionIsolatesOnlyTheFaultyChargerWhileKeepingItsHealthyBackupAvailable() {
        Company company = Company.builder().id(3L).agentMode(CompanyAgentMode.AUTOPILOT)
                .agentAutoDisableFaultyChargers(true).build();
        ChargingStation station = ChargingStation.builder().id(9L).name("Kanpur Central").address("Kanpur")
                .operatorCompanyId(3L).availability(StationAvailability.AVAILABLE).connectors(new ArrayList<>()).build();
        ChargingConnector faulty = ChargingConnector.builder().id(21L).station(station).chargerCode("KNP-03")
                .type(ConnectorType.CCS2).status(ChargerStatus.FAULT).available(true).build();
        ChargingConnector backup = ChargingConnector.builder().id(22L).station(station).chargerCode("KNP-01")
                .type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build();
        station.getConnectors().addAll(List.of(faulty, backup));
        when(verificationService.requireMarketplaceVerified(7L)).thenReturn(company);
        when(connectorRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(faulty));
        when(autopilotService.handleConnectorUnavailable(9L, "CCS2", 21L, "Cooling fault"))
                .thenReturn(Map.of("affectedJourneys", 0));

        CompanyAgentActionResponse response = service.executeAgentAction(7L,
                new CompanyAgentActionRequest(CompanyAgentActionType.DISABLE_NEW_BOOKINGS,
                        21L, null, null, null, "Cooling fault", true));

        assertThat(response.state()).isEqualTo("EXECUTED");
        assertThat(faulty.isAvailable()).isFalse();
        assertThat(backup.isAvailable()).isTrue();
        assertThat(station.getAvailability()).isEqualTo(StationAvailability.AVAILABLE);
        verify(connectorRepository).save(faulty);
        verify(activityLogRepository).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(CompanyAgentActionType.class)
    void evenAutopilotRequiresApprovalForEveryWrite(CompanyAgentActionType action) {
        Company company = Company.builder().id(3L).agentMode(CompanyAgentMode.AUTOPILOT).build();
        when(verificationService.requireMarketplaceVerified(7L)).thenReturn(company);
        assertThat(service.executeAgentAction(7L, new CompanyAgentActionRequest(action, 21L, 9L, 12.0, null, "Review", false)).state()).isEqualTo("AWAITING_APPROVAL");
        verifyNoInteractions(connectorRepository, stationRepository, maintenanceTicketRepository, autopilotService, activityLogRepository);
    }

    @Test void approvedDemoFaultPersistsTelemetryAndPropagatesOnlyTheExactConnector() {
        Company company = Company.builder().id(3L).agentMode(CompanyAgentMode.ASK_BEFORE_ACTIONS).build();
        ChargingStation station = ChargingStation.builder().id(9L).name("Agra Demo Charging Hub").demoData(true).demoSeedKey("AGRA_DEMO_01")
                .operatorCompanyId(3L).ownershipType(StationOwnershipType.HOST_PARTNERED).hostUserId(88L).connectors(new ArrayList<>()).build();
        ChargingConnector target = ChargingConnector.builder().id(21L).station(station).chargerCode("DEMO-AGRA-CCS2-01")
                .type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build();
        ChargingConnector backup = ChargingConnector.builder().id(22L).station(station).chargerCode("DEMO-AGRA-CCS2-02")
                .type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build();
        station.getConnectors().addAll(List.of(target, backup));
        when(verificationService.requireMarketplaceVerified(7L)).thenReturn(company);
        when(connectorRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(target));
        when(autopilotService.handleConnectorUnavailable(eq(9L), eq("CCS2"), eq(21L), anyString())).thenReturn(Map.of("affectedJourneys", 1));
        var result = service.executeAgentAction(7L, new CompanyAgentActionRequest(CompanyAgentActionType.SIMULATE_DEMO_FAULT,
                21L, 9L, null, null, "Simulated communication failure", true, ChargerStatus.ONLINE));
        assertThat(result.state()).isEqualTo("EXECUTED");
        assertThat(target.getStatus()).isEqualTo(ChargerStatus.FAULT);
        assertThat(target.getFaultCode()).isEqualTo("DEMO_CHARGER_FAULT");
        assertThat(target.getStatusSource()).isEqualTo("COMPANY_DEMO_CONTROL");
        assertThat(target.isMaintenanceMode()).isFalse();
        assertThat(backup.isAvailable()).isTrue();
        assertThat(station.getAvailability()).isEqualTo(StationAvailability.AVAILABLE);
        verify(adminControlService).recordDetectedIncident(eq(station), eq(target), any(), anyString(), eq(0), any());
    }

    @Test void anotherOperatorCannotControlEquipmentItOnlySupplied() {
        Company company = Company.builder().id(3L).agentMode(CompanyAgentMode.ASK_BEFORE_ACTIONS).build();
        ChargingStation station = ChargingStation.builder().id(9L).operatorCompanyId(99L).supplierCompanyId(3L).build();
        ChargingConnector target = ChargingConnector.builder().id(21L).station(station).type(ConnectorType.CCS2).build();
        when(verificationService.requireMarketplaceVerified(7L)).thenReturn(company);
        when(connectorRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(target));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.executeAgentAction(7L,
                new CompanyAgentActionRequest(CompanyAgentActionType.DISABLE_NEW_BOOKINGS, 21L, null, null, null, "Wrong operator", true)))
                .isInstanceOf(com.vidyut.common.exception.ResourceNotFoundException.class);
        verify(connectorRepository, never()).save(any());
    }

    @Test void requested120KwExpansionExcludesInsufficientPowerAndRespectsBays() {
        Company company = Company.builder().id(3L).build();
        when(companyRepository.findByAccount_Id(7L)).thenReturn(Optional.of(company));
        when(operatorContextService.inspect(any(), any(), any(), any())).thenReturn(Map.of());
        var small = com.vidyut.land.entity.LandListing.builder().id(1L).title("Small site").latitude(27).longitude(78)
                .availableParkingBays(8).availableLoadKw(80).preferredConnectorType("CCS2").build();
        var ready = com.vidyut.land.entity.LandListing.builder().id(2L).title("Ready site").latitude(27).longitude(78)
                .availableParkingBays(1).availableLoadKw(250).preferredConnectorType("CCS2").build();
        when(landListingRepository.findByDiscoverableTrueAndStatusIn(any())).thenReturn(List.of(small, ready));
        var result = service.askAssistant(7L, "Which Host property is the best candidate for our next 120 kW CCS2 charger?");
        assertThat(result.siteRecommendations()).hasSize(1);
        assertThat(result.siteRecommendations().get(0).propertyId()).isEqualTo(2L);
        assertThat(result.siteRecommendations().get(0).recommendedPowerKw()).isEqualTo(120);
        assertThat(result.siteRecommendations().get(0).recommendedChargerCount()).isEqualTo(1);
        assertThat(result.siteRecommendations().get(0).nearestActiveStationKm()).isNull();
        verifyNoInteractions(activityLogRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void unsafeDemoApprovalsDoNotChangeHardware(boolean stale) {
        when(verificationService.requireMarketplaceVerified(7L)).thenReturn(Company.builder().id(3L).build());
        var station = ChargingStation.builder().id(9L).operatorCompanyId(3L).demoData(stale).demoSeedKey("AGRA_DEMO_01").build();
        var charger = ChargingConnector.builder().id(21L).station(station).chargerCode("DEMO-AGRA-CCS2-01")
                .type(ConnectorType.CCS2).status(stale ? ChargerStatus.FAULT : ChargerStatus.ONLINE).build();
        when(connectorRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(charger));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.executeAgentAction(7L,
                new CompanyAgentActionRequest(CompanyAgentActionType.SIMULATE_DEMO_FAULT, 21L, null, null, null, "Synthetic fault", true, ChargerStatus.ONLINE)))
                .isInstanceOf(com.vidyut.common.exception.BadRequestException.class);
        verify(connectorRepository, never()).save(any());
        verifyNoInteractions(autopilotService, adminControlService);
    }
}
