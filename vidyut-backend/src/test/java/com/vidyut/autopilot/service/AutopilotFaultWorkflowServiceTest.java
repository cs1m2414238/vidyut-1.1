package com.vidyut.autopilot.service;

import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.entity.NetworkIncident;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutopilotFaultWorkflowServiceTest {

    private final AutopilotService autopilotService = mock(AutopilotService.class);
    private final AutopilotTripRepository tripRepository = mock(AutopilotTripRepository.class);
    private final AutopilotStopRepository stopRepository = mock(AutopilotStopRepository.class);
    private final ChargingStationRepository stationRepository = mock(ChargingStationRepository.class);
    private final ChargingConnectorRepository connectorRepository = mock(ChargingConnectorRepository.class);
    private final AdminControlService adminControlService = mock(AdminControlService.class);
    private final AutopilotFaultWorkflowService service = new AutopilotFaultWorkflowService(
            autopilotService, tripRepository, stopRepository, stationRepository, connectorRepository, adminControlService);

    @Test
    void driverReportPropagatesIncidentWithoutChangingHardware() {
        AutopilotStop stop = AutopilotStop.builder().id(410L).tripId(41L).stationId(10L).stationName("Kanpur Hub")
                .stationAddress("NH-19").connectorType("CCS2").powerKw(60)
                .status(AutopilotStopStatus.RESERVED).build();
        ChargingConnector failed = connector(11L, ConnectorType.CCS2, 60);
        ChargingConnector type2Backup = connector(12L, ConnectorType.TYPE2, 22);
        ChargingStation station = station(10L, failed, type2Backup);
        AutopilotTripResponse rerouted = AutopilotTripResponse.builder()
                .id(41L).status(AutopilotTripStatus.REROUTED).build();
        AutopilotTrip trip = AutopilotTrip.builder().userId(7L).id(41L).currentBatteryPercent(50.0).build();
        when(tripRepository.findById(41L)).thenReturn(Optional.of(trip));
        when(stopRepository.findFirstByTripIdAndStatusOrderBySequenceNumberAsc(41L, AutopilotStopStatus.RESERVED))
                .thenReturn(Optional.of(stop));
        when(stationRepository.findById(10L)).thenReturn(Optional.of(station));
        when(autopilotService.simulateChargerFault(41L, 7L, 410L)).thenReturn(rerouted);
        when(adminControlService.recordDetectedIncident(eq(station), eq(failed), eq(IncidentSeverity.HIGH),
                any(), eq(0), any())).thenReturn(NetworkIncident.builder()
                        .incidentCode("INC-DEMO-1").maintenanceTicketId(91L).build());
        when(autopilotService.recordOperationalPropagation(41L, 7L, "INC-DEMO-1", 91L))
                .thenReturn(rerouted);

        AutopilotTripResponse result = service.simulateAndPropagate(41L, 7L);

        assertThat(result.getStatus()).isEqualTo(AutopilotTripStatus.REROUTED);
        assertThat(failed.getStatus()).isEqualTo(ChargerStatus.ONLINE);
        assertThat(failed.isMaintenanceMode()).isFalse();
        assertThat(failed.isAvailable()).isTrue();
        assertThat(failed.getFaultCode()).isNull();
        org.mockito.Mockito.verifyNoInteractions(connectorRepository);
        verify(autopilotService).recordOperationalPropagation(41L, 7L, "INC-DEMO-1", 91L);
        ArgumentCaptor<Map<String, Object>> impact = mapCaptor();
        verify(adminControlService).recordDetectedIncident(eq(station), eq(failed),
                eq(IncidentSeverity.HIGH), any(), eq(0), impact.capture());
        assertThat(impact.getValue()).containsEntry("affectedJourneys", 1)
                .containsEntry("automaticReroutes", 1)
                .containsEntry("driverApprovals", 0)
                .containsEntry("replanRequired", 0);
    }

    @Test
    void recordsApprovalRequiredInsteadOfClaimingAutomaticExecution() {
        AutopilotStop stop = AutopilotStop.builder().id(420L).tripId(42L).stationId(20L).stationName("Jhansi Hub")
                .stationAddress("Bypass").connectorType("CCS2").powerKw(120)
                .status(AutopilotStopStatus.RESERVED).build();
        ChargingConnector failed = connector(21L, ConnectorType.CCS2, 120);
        ChargingStation station = station(20L, failed);
        AutopilotTripResponse awaitingApproval = AutopilotTripResponse.builder()
                .id(42L).status(AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED).build();
        AutopilotTrip trip = AutopilotTrip.builder().userId(8L).id(42L).currentBatteryPercent(50.0).build();
        when(tripRepository.findById(42L)).thenReturn(Optional.of(trip));
        when(stopRepository.findFirstByTripIdAndStatusOrderBySequenceNumberAsc(42L, AutopilotStopStatus.RESERVED))
                .thenReturn(Optional.of(stop));
        when(stationRepository.findById(20L)).thenReturn(Optional.of(station));
        when(autopilotService.simulateChargerFault(42L, 8L, 420L)).thenReturn(awaitingApproval);
        when(adminControlService.recordDetectedIncident(eq(station), eq(failed), eq(IncidentSeverity.HIGH),
                any(), eq(0), any())).thenReturn(NetworkIncident.builder()
                        .incidentCode("INC-DEMO-2").build());
        when(autopilotService.recordOperationalPropagation(42L, 8L, "INC-DEMO-2", null))
                .thenReturn(awaitingApproval);

        service.simulateAndPropagate(42L, 8L);

        assertThat(station.getAvailability()).isEqualTo(StationAvailability.AVAILABLE);
        verify(stationRepository, org.mockito.Mockito.never()).save(station);
        ArgumentCaptor<Map<String, Object>> impact = mapCaptor();
        verify(adminControlService).recordDetectedIncident(eq(station), eq(failed),
                eq(IncidentSeverity.HIGH), any(), eq(0), impact.capture());
        assertThat(impact.getValue()).containsEntry("automaticReroutes", 0)
                .containsEntry("driverApprovals", 1)
                .containsEntry("replanRequired", 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    private ChargingStation station(Long id, ChargingConnector... connectors) {
        ChargingStation station = ChargingStation.builder().id(id).name("Demo station").address("Demo address")
                .availability(StationAvailability.AVAILABLE).connectors(List.of(connectors)).build();
        for (ChargingConnector connector : connectors) connector.setStation(station);
        return station;
    }

    private ChargingConnector connector(Long id, ConnectorType type, double powerKw) {
        return ChargingConnector.builder().id(id).type(type).powerKw(powerKw).chargerCode("DEMO-" + id)
                .available(true).status(ChargerStatus.ONLINE).healthScore(96).build();
    }
}
