package com.vidyut.admin.service;

import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.admin.dto.IncidentCreateRequest;
import com.vidyut.admin.dto.PropertyWorkflowRequest;
import com.vidyut.admin.entity.*;
import com.vidyut.admin.repository.*;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.company.repository.CompanyMaintenanceTicketRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.service.CompanyVerificationService;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.repository.InstallationProposalRepository;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControlServiceTest {
    @Mock AdminAccessService access;
    @Mock AdminAuditLogRepository auditRepository;
    @Mock AccountRepository accountRepository;
    @Mock HostProfileRepository hostRepository;
    @Mock CompanyRepository companyRepository;
    @Mock CompanyVerificationService companyVerificationService;
    @Mock LandListingRepository landRepository;
    @Mock ChargingStationRepository stationRepository;
    @Mock ChargingConnectorRepository connectorRepository;
    @Mock ChargingSessionRepository sessionRepository;
    @Mock BookingRepository bookingRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock CompanyMaintenanceTicketRepository maintenanceRepository;
    @Mock NetworkIncidentRepository incidentRepository;
    @Mock AdminSupportCaseRepository supportRepository;
    @Mock AdminGreenSchemeRepository schemeRepository;
    @Mock AdminSettlementRepository settlementRepository;
    @Mock InstallationProposalRepository proposalRepository;
    @Mock AutopilotService autopilotService;
    @Mock NotificationService notificationService;
    @InjectMocks AdminControlService service;

    @Test
    void publishesPropertyOnlyAfterHostAndSiteEvidenceAreVerified() {
        AdminAccount admin = AdminAccount.builder().accountId(50L).adminRole(AdminRole.VERIFICATION_ADMIN).build();
        LandListing property = LandListing.builder().id(7L).hostUserId(11L).title("Verified site").address("Kanpur")
                .ownershipDocumentUrl("https://evidence/ownership").electricityDocumentUrl("https://evidence/power")
                .videoVerificationUrl("https://evidence/video").availableParkingBays(10).availableLoadKw(180).build();
        when(access.require(AdminCapability.VERIFICATIONS)).thenReturn(admin);
        when(landRepository.findById(7L)).thenReturn(Optional.of(property));
        when(hostRepository.findById(11L)).thenReturn(Optional.of(HostProfile.builder().verified(true).build()));

        Map<String, Object> result = service.propertyWorkflow(7L,
                new PropertyWorkflowRequest("APPROVE", "All evidence verified", null));

        assertThat(result.get("stage")).isEqualTo("PUBLISHED");
        assertThat(property.getStatus()).isEqualTo(LandListingStatus.APPROVED);
        assertThat(property.isDiscoverable()).isTrue();
        assertThat(property.isVideoVerified()).isTrue();
        assertThat(property.getPropertyScore()).isGreaterThan(50);
        verify(auditRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void chargerFaultCreatesIncidentAndDelegatesReroutingToAutopilotBackend() {
        AdminAccount admin = AdminAccount.builder().accountId(60L).adminRole(AdminRole.OPERATIONS_ADMIN).build();
        ChargingStation station = ChargingStation.builder().id(9L).name("Kanpur Hub").address("NH 19")
                .availability(StationAvailability.AVAILABLE).connectors(new ArrayList<>()).build();
        ChargingConnector connector = ChargingConnector.builder().id(21L).station(station).chargerCode("KNP-03")
                .type(ConnectorType.CCS2).powerKw(150).available(true).status(ChargerStatus.ONLINE).build();
        station.getConnectors().add(connector);
        when(access.require(AdminCapability.OPERATIONS)).thenReturn(admin);
        when(connectorRepository.findById(21L)).thenReturn(Optional.of(connector));
        when(incidentRepository.findFirstByConnectorIdAndStatusInOrderByCreatedAtDesc(eq(21L), anyCollection())).thenReturn(Optional.empty());
        when(autopilotService.handleConnectorUnavailable(9L, "CCS2", 21L, "Cooling fault"))
                .thenReturn(Map.of("affectedJourneys", 2, "automaticReroutes", 1, "driverApprovals", 1,
                        "replanRequired", 0, "backupConnectorAvailable", false));
        when(incidentRepository.save(any(NetworkIncident.class))).thenAnswer(invocation -> {
            NetworkIncident incident = invocation.getArgument(0);
            incident.setId(81L);
            return incident;
        });

        NetworkIncident incident = service.createIncident(new IncidentCreateRequest(21L, IncidentSeverity.CRITICAL,
                "Cooling fault", 180));

        assertThat(connector.getStatus()).isEqualTo(ChargerStatus.FAULT);
        assertThat(connector.isMaintenanceMode()).isTrue();
        assertThat(station.getAvailability()).isEqualTo(StationAvailability.UNAVAILABLE);
        assertThat(incident.getAffectedBookings()).isEqualTo(2);
        assertThat(incident.getUsersRerouted()).isEqualTo(1);
        assertThat(incident.getApprovalsRequired()).isEqualTo(1);
        verify(autopilotService).handleConnectorUnavailable(9L, "CCS2", 21L, "Cooling fault");
        verify(auditRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void agentDetectedFaultCreatesSystemAuditEvidence() {
        ChargingStation station = ChargingStation.builder().id(12L).name("Jhansi Hub").address("Bypass")
                .connectors(new ArrayList<>()).build();
        ChargingConnector connector = ChargingConnector.builder().id(31L).station(station).chargerCode("JHS-02")
                .type(ConnectorType.CCS2).status(ChargerStatus.FAULT).faultCode("HEARTBEAT_LOSS").build();
        station.getConnectors().add(connector);
        when(incidentRepository.findFirstByConnectorIdAndStatusInOrderByCreatedAtDesc(eq(31L), anyCollection()))
                .thenReturn(Optional.empty());
        when(incidentRepository.save(any(NetworkIncident.class))).thenAnswer(invocation -> {
            NetworkIncident incident = invocation.getArgument(0);
            incident.setId(91L);
            return incident;
        });

        NetworkIncident incident = service.recordDetectedIncident(station, connector, IncidentSeverity.CRITICAL,
                "Autopilot heartbeat failure", 180,
                Map.of("affectedJourneys", 1, "automaticReroutes", 1, "driverApprovals", 0,
                        "replanRequired", 0));

        assertThat(incident.getUsersRerouted()).isEqualTo(1);
        var audit = org.mockito.ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().getAdminAccountId()).isZero();
        assertThat(audit.getValue().getAction()).isEqualTo("AUTOPILOT_INCIDENT_DETECTED");
        assertThat(audit.getValue().getReason()).contains("affected=1", "rerouted=1");
    }
}
