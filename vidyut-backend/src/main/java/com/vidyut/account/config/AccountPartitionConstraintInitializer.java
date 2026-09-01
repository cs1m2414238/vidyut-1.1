package com.vidyut.account.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
public class AccountPartitionConstraintInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql")) {
                return;
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Company accounts can be created with the essential credentials first;
        // legal identity fields are filled by the profile-completion flow.
        jdbc.execute("ALTER TABLE companies ALTER COLUMN company_name DROP NOT NULL");
        jdbc.execute("ALTER TABLE companies ALTER COLUMN registration_number DROP NOT NULL");
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION enforce_vidyut_account_partition()
                RETURNS trigger LANGUAGE plpgsql AS $$
                DECLARE
                    target_id bigint;
                    target_type varchar(20);
                    total_roles integer;
                    invalid_roles integer;
                BEGIN
                    IF TG_TABLE_NAME = 'accounts' THEN
                        target_id := COALESCE(NEW.id, OLD.id);
                    ELSE
                        target_id := COALESCE(NEW.account_id, OLD.account_id);
                    END IF;

                    SELECT account_type INTO target_type FROM accounts WHERE id = target_id;
                    IF NOT FOUND THEN
                        RETURN NULL;
                    END IF;

                    SELECT COUNT(*) INTO total_roles FROM account_roles WHERE account_id = target_id;
                    IF target_type = 'INDIVIDUAL' THEN
                        SELECT COUNT(*) INTO invalid_roles FROM account_roles
                        WHERE account_id = target_id AND role NOT IN ('ROLE_EV_USER', 'ROLE_HOST');
                        IF total_roles = 0 OR invalid_roles > 0 THEN
                            RAISE EXCEPTION 'Individual account % must have EV_USER and/or HOST only', target_id;
                        END IF;
                        IF EXISTS (SELECT 1 FROM account_roles WHERE account_id = target_id AND role = 'ROLE_EV_USER')
                           AND NOT EXISTS (SELECT 1 FROM ev_user_profiles WHERE account_id = target_id) THEN
                            RAISE EXCEPTION 'EV account % requires an EV user profile', target_id;
                        END IF;
                        IF EXISTS (SELECT 1 FROM account_roles WHERE account_id = target_id AND role = 'ROLE_HOST')
                           AND NOT EXISTS (SELECT 1 FROM host_profiles WHERE account_id = target_id) THEN
                            RAISE EXCEPTION 'Host account % requires a host profile', target_id;
                        END IF;
                    ELSIF target_type = 'COMPANY' THEN
                        SELECT COUNT(*) INTO invalid_roles FROM account_roles
                        WHERE account_id = target_id AND role = 'ROLE_COMPANY';
                        IF total_roles <> 1 OR invalid_roles <> 1 THEN
                            RAISE EXCEPTION 'Company account % must have only ROLE_COMPANY', target_id;
                        END IF;
                        IF NOT EXISTS (SELECT 1 FROM companies WHERE account_id = target_id) THEN
                            RAISE EXCEPTION 'Company account % requires a company profile', target_id;
                        END IF;
                    ELSIF target_type = 'ADMIN' THEN
                        SELECT COUNT(*) INTO invalid_roles FROM account_roles
                        WHERE account_id = target_id AND role = 'ROLE_ADMIN';
                        IF total_roles <> 1 OR invalid_roles <> 1 THEN
                            RAISE EXCEPTION 'Admin account % must have only ROLE_ADMIN', target_id;
                        END IF;
                    ELSE
                        RAISE EXCEPTION 'Invalid account type for account %', target_id;
                    END IF;
                    RETURN NULL;
                END;
                $$
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS accounts_partition_check ON accounts");
        jdbc.execute("""
                CREATE CONSTRAINT TRIGGER accounts_partition_check
                AFTER INSERT OR UPDATE ON accounts
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION enforce_vidyut_account_partition()
                """);
        jdbc.execute("DROP TRIGGER IF EXISTS account_roles_partition_check ON account_roles");
        jdbc.execute("""
                CREATE CONSTRAINT TRIGGER account_roles_partition_check
                AFTER INSERT OR UPDATE OR DELETE ON account_roles
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION enforce_vidyut_account_partition()
                """);
        createProfileTrigger(jdbc, "ev_user_profiles", "ev_profiles_partition_check");
        createProfileTrigger(jdbc, "host_profiles", "host_profiles_partition_check");
        createProfileTrigger(jdbc, "companies", "companies_partition_check");
    }

    private void createProfileTrigger(JdbcTemplate jdbc, String table, String trigger) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON " + table);
        jdbc.execute("CREATE CONSTRAINT TRIGGER " + trigger + " AFTER INSERT OR UPDATE OR DELETE ON " + table
                + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW "
                + "EXECUTE FUNCTION enforce_vidyut_account_partition()");
    }
}
