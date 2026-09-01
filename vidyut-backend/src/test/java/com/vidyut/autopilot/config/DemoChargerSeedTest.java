package com.vidyut.autopilot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoChargerSeedTest {

    private static final Set<String> CENTRAL_INDIA_ADDITIONS = Set.of(
            "LTP", "SVP", "GNA", "BRA", "BIN", "SEH", "DWS", "RTM", "MDS",
            "DMO", "NRS", "KTE", "STA", "REW", "PAN", "SDH", "SGRL", "NAR", "ET",
            "BTL", "CWA", "SEI", "MDL", "KNW", "BAU", "AMI");

    @Test
    void centralIndiaSeedHasUniqueUsableHighwayStations() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("demo/chargers-india.json")) {
            assertThat(input).isNotNull();
            JsonNode stations = new ObjectMapper().readTree(input);
            assertThat(stations.isArray()).isTrue();
            assertThat(stations.size()).isGreaterThanOrEqualTo(112);

            Set<String> keys = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (JsonNode station : stations) {
                assertThat(keys.add(station.path("key").asText())).isTrue();
                assertThat(names.add(station.path("name").asText())).isTrue();
                assertThat(station.path("latitude").asDouble()).isBetween(6.0, 38.0);
                assertThat(station.path("longitude").asDouble()).isBetween(68.0, 98.0);
                assertThat(station.path("powerKw").asDouble()).isGreaterThanOrEqualTo(120.0);
                assertThat(station.path("pricePerKwh").asDouble()).isPositive();
            }

            assertThat(keys).containsAll(CENTRAL_INDIA_ADDITIONS);
        }
    }
}
