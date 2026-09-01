package com.vidyut.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DemoNetworkResourceTest {

    private static final Set<String> CANONICAL_HIGHWAY_KEYS = Set.of(
            "NOI", "MTR", "AGR", "GWL", "JHS", "LTP", "BIN", "VDH", "BHO");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void districtDatasetIsNormalizedUniqueAndNationwide() throws Exception {
        List<Map<String, Object>> rows;
        try (var input = new ClassPathResource("demo/district-chargers-india.json").getInputStream()) {
            rows = objectMapper.readValue(input, new TypeReference<>() {});
        }

        Set<String> keys = rows.stream().map(row -> String.valueOf(row.get("key"))).collect(Collectors.toSet());
        Set<String> stateDistrictPairs = rows.stream()
                .map(row -> String.valueOf(row.get("state")).trim().toLowerCase() + "|"
                        + String.valueOf(row.get("district")).trim().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> states = rows.stream()
                .map(row -> String.valueOf(row.get("state")).trim())
                .collect(Collectors.toSet());

        assertThat(rows).hasSize(777);
        assertThat(keys).hasSize(rows.size());
        assertThat(stateDistrictPairs).hasSize(rows.size());
        assertThat(states).hasSize(36);
        assertThat(rows).allSatisfy(row -> {
            assertThat(((Number) row.get("latitude")).doubleValue()).isBetween(6.0, 38.0);
            assertThat(((Number) row.get("longitude")).doubleValue()).isBetween(68.0, 98.0);
        });
    }

    @Test
    void highwayDatasetContributesOneHundredThreeNonCanonicalHubs() throws Exception {
        List<Map<String, Object>> rows;
        try (var input = new ClassPathResource("demo/chargers-india.json").getInputStream()) {
            rows = objectMapper.readValue(input, new TypeReference<>() {});
        }

        Set<String> keys = rows.stream().map(row -> String.valueOf(row.get("key"))).collect(Collectors.toSet());
        assertThat(rows).hasSize(112);
        assertThat(keys).hasSize(rows.size());
        assertThat(keys).containsAll(CANONICAL_HIGHWAY_KEYS);
        assertThat(keys.stream().filter(key -> !CANONICAL_HIGHWAY_KEYS.contains(key))).hasSize(103);
    }
}
