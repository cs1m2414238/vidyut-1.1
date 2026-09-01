package com.vidyut.vehicle;

import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleTelemetryMigrationTest {

    private static final String MIGRATION =
            "/db/migration/postgresql/V2__allow_bluetooth_demo_vehicle_telemetry.sql";
    private static final Pattern QUOTED_ENUM_VALUE = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    @Test
    void postgresConstraintMatchesEveryJavaTelemetrySourceExactly() throws IOException {
        String sql;
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("PostgreSQL vehicle telemetry migration").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher matcher = QUOTED_ENUM_VALUE.matcher(sql);
        Set<String> migrationValues = matcher.results()
                .map(result -> result.group(1))
                .collect(Collectors.toSet());
        Set<String> enumValues = Arrays.stream(VehicleTelemetrySource.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(migrationValues).isEqualTo(enumValues);
        assertThat(migrationValues).contains("BLUETOOTH_DEMO");
    }
}
