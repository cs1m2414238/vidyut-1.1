package com.vidyut.autopilot.service;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class RecoveryMigrationTest {
    @Test void additiveMigrationPreservesExistingRowsAndDoesNotInventTelemetry() throws Exception {
        try(var db=DriverManager.getConnection("jdbc:h2:mem:recovery_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"); var sql=db.createStatement()) {
            sql.execute("create table autopilot_trips(id bigint primary key, current_battery_percent double precision)");
            sql.execute("create table bookings(id bigint primary key, start_time timestamp, end_time timestamp)");
            sql.execute("insert into autopilot_trips values (1,39)");
            sql.execute("insert into bookings(id) values (7)");
            String migration;
            try(var stream=getClass().getResourceAsStream("/db/migration/postgresql/V27__capture_autopilot_recovery_state.sql")) {
                migration=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            }
            for(int run=0;run<2;run++) for(String statement:migration.replaceAll("(?m)^--.*$", "").split(";")) if(!statement.isBlank())sql.execute(statement);
            try(var rows=sql.executeQuery("select current_battery_percent,current_latitude,distance_travelled_km from autopilot_trips where id=1")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getDouble(1)).isEqualTo(39);
                assertThat(rows.getObject(2)).isNull(); assertThat(rows.getDouble(3)).isZero();
            }
            try(var rows=sql.executeQuery("select connector_id from bookings where id=7")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getObject(1)).isNull();
            }
        }
    }
}
