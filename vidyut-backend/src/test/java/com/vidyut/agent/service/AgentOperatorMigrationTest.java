package com.vidyut.agent.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentOperatorMigrationTest {
    @Test
    void migrationCreatesDurableEventsAndAccountScopedIdempotentWorkItems() throws Exception {
        try (var db = DriverManager.getConnection(
                "jdbc:h2:mem:agent_operator_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             var sql = db.createStatement()) {
            sql.execute("create table accounts(id bigint primary key)");
            sql.execute("insert into accounts values (7), (8)");
            String migration;
            try (var stream = getClass().getResourceAsStream(
                    "/db/migration/postgresql/V28__agent_operator_work_queue.sql")) {
                assertThat(stream).isNotNull();
                migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String statement : migration.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
            sql.execute("insert into agent_domain_events(event_key,event_type,aggregate_type,aggregate_id,actor_account_id,payload_json) "
                    + "values ('fault:1','CONNECTOR_FAULTED','CONNECTOR',21,7,'{}')");
            sql.execute("insert into agent_work_items(account_id,workspace,work_key,category,status,priority,title,detail) "
                    + "values (7,'COMPANY','incident:1','INCIDENT_TRIAGE','ATTENTION','CRITICAL','Fault','Review')");
            sql.execute("insert into agent_work_items(account_id,workspace,work_key,category,status,priority,title,detail) "
                    + "values (8,'HOST','incident:1','HOSTED_CHARGER_INCIDENT','ATTENTION','HIGH','Hosted fault','Monitor')");

            try (var rows = sql.executeQuery("select count(*) from agent_work_items where work_key='incident:1'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> sql.execute(
                    "insert into agent_work_items(account_id,workspace,work_key,category,status,priority,title,detail) "
                            + "values (7,'COMPANY','incident:1','TEST','DONE','LOW','Duplicate','Duplicate')"))
                    .hasMessageContaining("UK_AGENT_WORK_ITEM_SCOPE_KEY");

            String hardening;
            try (var stream = getClass().getResourceAsStream(
                    "/db/migration/postgresql/V28_1__harden_agent_operator_foundation.sql")) {
                assertThat(stream).isNotNull();
                hardening = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String statement : hardening.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
            try (var row = sql.executeQuery("select idempotency_key, correlation_id from agent_work_items where account_id=7")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("work-item-1");
                assertThat(row.getString(2)).isEqualTo("legacy-work-item-1");
            }
            sql.execute("insert into agent_outbox_events(event_key,event_type,aggregate_type,aggregate_id,correlation_id,payload_json) "
                    + "values ('outbox:1','CONNECTOR_FAULTED','CONNECTOR',21,'connector-21','{}')");
            sql.execute("insert into agent_activities(account_id,workspace,correlation_id,activity_type,summary) "
                    + "values (7,'COMPANY','connector-21','FAULT_DETECTED','Fault detected')");
            assertThatThrownBy(() -> sql.execute(
                    "insert into agent_work_items(account_id,workspace,work_key,idempotency_key,correlation_id,category,status,priority,title,detail) "
                            + "values (7,'COMPANY','another','work-item-1','trace','TEST','PENDING','LOW','Duplicate','Duplicate')"))
                    .hasMessageContaining("UK_AGENT_WORK_ITEM_IDEMPOTENCY");
        }
    }
}
