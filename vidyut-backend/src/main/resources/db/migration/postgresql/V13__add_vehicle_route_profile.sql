ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS efficiency_wh_per_km double precision NOT NULL DEFAULT 140,
    ADD COLUMN IF NOT EXISTS max_ac_charge_power_kw double precision NOT NULL DEFAULT 7.2;

CREATE TABLE IF NOT EXISTS vehicle_supported_connectors (
    vehicle_id bigint NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    connector_type varchar(20) NOT NULL,
    PRIMARY KEY (vehicle_id, connector_type),
    CONSTRAINT vehicle_supported_connector_type_check CHECK (
        connector_type IN ('TYPE1', 'TYPE2', 'CCS2', 'CHADEMO', 'GB_T')
    )
);

INSERT INTO vehicle_supported_connectors (vehicle_id, connector_type)
SELECT id,
       CASE
           WHEN upper(regexp_replace(connector_type, '[^A-Z0-9]', '', 'g')) = 'TYPE1' THEN 'TYPE1'
           WHEN upper(regexp_replace(connector_type, '[^A-Z0-9]', '', 'g')) = 'TYPE2' THEN 'TYPE2'
           WHEN upper(regexp_replace(connector_type, '[^A-Z0-9]', '', 'g')) IN ('CHADEMO', 'CHADEMO') THEN 'CHADEMO'
           WHEN upper(regexp_replace(connector_type, '[^A-Z0-9]', '', 'g')) IN ('GBT', 'BHARATDC001') THEN 'GB_T'
           ELSE 'CCS2'
       END
FROM vehicles
WHERE connector_type IS NOT NULL
ON CONFLICT DO NOTHING;
