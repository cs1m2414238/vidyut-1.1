DO $$
BEGIN
    IF to_regclass('vehicles') IS NOT NULL THEN
        ALTER TABLE vehicles
            DROP CONSTRAINT IF EXISTS vehicles_telemetry_source_check;

        ALTER TABLE vehicles
            ADD CONSTRAINT vehicles_telemetry_source_check
            CHECK (telemetry_source IN (
                'BLUETOOTH',
                'BLUETOOTH_DEMO',
                'MANUAL',
                'CHARGING_SESSION',
                'NOT_AVAILABLE'
            ));
    END IF;
END
$$;
