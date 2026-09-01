package com.vidyut.autopilot.service;

import com.vidyut.autopilot.entity.*;
import com.vidyut.autopilot.repository.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorIncidentImpactTest {
    @Mock ChargingStationRepository stationRepository;
    @Mock AutopilotStopRepository stopRepository;
    @Mock AutopilotTripRepository tripRepository;
    @InjectMocks AutopilotService service;

    @Test void onlyJourneyUsingTheFaultedConnectorIsAffectedEvenWhenAnotherCcs2IsHealthy() {
        ChargingConnector failed = ChargingConnector.builder().id(11L).type(ConnectorType.CCS2).status(ChargerStatus.FAULT).build();
        ChargingConnector healthy = ChargingConnector.builder().id(12L).type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build();
        ChargingStation station = ChargingStation.builder().id(9L).connectors(List.of(failed, healthy)).build();
        when(stationRepository.findById(9L)).thenReturn(Optional.of(station));
        when(stopRepository.findByStationIdAndStatus(9L, AutopilotStopStatus.RESERVED)).thenReturn(List.of(
                AutopilotStop.builder().tripId(1L).connectorId(11L).connectorType("CCS2").build(),
                AutopilotStop.builder().tripId(2L).connectorId(12L).connectorType("CCS2").build()));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(AutopilotTrip.builder().id(1L).status(AutopilotTripStatus.MONITORING).autonomyMode("ASK_BEFORE_ACTIONS").build()));

        assertThat(service.connectorDisruptionImpact(9L, "CCS2", 11L))
                .containsEntry("activeJourneys", 1L).containsEntry("driverApprovals", 1L).containsEntry("backupConnectorAvailable", true);
        verify(tripRepository, never()).findById(2L);
    }

    @Test void legacyTypeOnlyReservationDoesNotPretendToKnowWhichConnectorItUses() {
        when(stationRepository.findById(9L)).thenReturn(Optional.of(ChargingStation.builder().id(9L).connectors(List.of(
                ChargingConnector.builder().id(12L).type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build())).build()));
        when(stopRepository.findByStationIdAndStatus(9L, AutopilotStopStatus.RESERVED)).thenReturn(List.of(
                AutopilotStop.builder().tripId(1L).connectorType("CCS2").build()));
        assertThat(service.connectorDisruptionImpact(9L, "CCS2", 11L)).containsEntry("activeJourneys", 0L);
        verifyNoInteractions(tripRepository);
    }
}
