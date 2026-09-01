package com.vidyut.autopilot.config;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistrictDemoChargerInitializerTest {

    @Mock ChargingStationRepository stationRepository;

    @Test
    void resourceContainsUniqueNationwideDistrictHeadquarters() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(DistrictDemoChargerInitializer.SEED_RESOURCE)) {
            assertThat(input).isNotNull();
            JsonNode districts = new ObjectMapper().readTree(input);
            assertThat(districts.isArray()).isTrue();
            assertThat(districts.size()).isGreaterThanOrEqualTo(750);

            Set<String> keys = new HashSet<>();
            Set<String> districtPairs = new HashSet<>();
            Set<String> statesAndTerritories = new HashSet<>();
            for (JsonNode district : districts) {
                String state = district.path("state").asText();
                String name = district.path("district").asText();
                assertThat(keys.add(district.path("key").asText())).isTrue();
                assertThat(districtPairs.add(state + "|" + name)).isTrue();
                statesAndTerritories.add(state);
                assertThat(district.path("latitude").asDouble()).isBetween(6.0, 38.0);
                assertThat(district.path("longitude").asDouble()).isBetween(68.0, 98.0);
            }
            assertThat(statesAndTerritories).hasSizeGreaterThanOrEqualTo(35);
        }
    }

    @Test
    void everyDistrictGetsEverySupportedConnectorType() throws Exception {
        when(stationRepository.findAll()).thenReturn(List.of());
        when(stationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        new DistrictDemoChargerInitializer(stationRepository, new ObjectMapper())
                .run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChargingStation>> stations = ArgumentCaptor.forClass(List.class);
        verify(stationRepository).saveAll(stations.capture());
        assertThat(stations.getValue()).hasSizeGreaterThanOrEqualTo(750);
        assertThat(stations.getValue()).allSatisfy(station -> {
            assertThat(station.isDemoData()).isTrue();
            assertThat(station.getDemoSeedKey()).startsWith("SOI-");
            assertThat(station.getConnectors()).hasSize(5);
            assertThat(station.getConnectors()).extracting(connector -> connector.getType())
                    .containsExactlyInAnyOrder(ConnectorType.CCS2, ConnectorType.TYPE2, ConnectorType.TYPE1,
                            ConnectorType.CHADEMO, ConnectorType.GB_T);
        });
        assertThat(stations.getValue().stream()
                .flatMap(station -> station.getConnectors().stream())
                .map(connector -> connector.getType())
                .collect(java.util.stream.Collectors.toSet()))
                .containsAll(Set.of(ConnectorType.CCS2, ConnectorType.TYPE2, ConnectorType.TYPE1,
                        ConnectorType.CHADEMO, ConnectorType.GB_T));
    }
}
