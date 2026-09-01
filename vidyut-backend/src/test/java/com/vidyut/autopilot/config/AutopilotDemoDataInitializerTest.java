package com.vidyut.autopilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutopilotDemoDataInitializerTest {

    @Mock ChargingStationRepository stationRepository;

    @Test
    void everyCuratedCorridorHubCarriesAllSupportedConnectorTypes() throws Exception {
        when(stationRepository.findAll()).thenReturn(List.of());
        when(stationRepository.save(any(ChargingStation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        new AutopilotDemoDataInitializer(stationRepository, new ObjectMapper())
                .run(new DefaultApplicationArguments());

        ArgumentCaptor<ChargingStation> stations = ArgumentCaptor.forClass(ChargingStation.class);
        verify(stationRepository, atLeastOnce()).save(stations.capture());
        assertThat(stations.getAllValues()).hasSizeGreaterThanOrEqualTo(112);
        assertThat(stations.getAllValues()).allSatisfy(station ->
                assertThat(station.getConnectors()).extracting(connector -> connector.getType())
                        .containsAll(Set.of(ConnectorType.CCS2, ConnectorType.TYPE2, ConnectorType.TYPE1,
                                ConnectorType.CHADEMO, ConnectorType.GB_T)));
    }
}
