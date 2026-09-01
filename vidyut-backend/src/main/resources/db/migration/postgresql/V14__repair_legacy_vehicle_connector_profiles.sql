DELETE FROM vehicle_supported_connectors supported
USING vehicles vehicle
WHERE supported.vehicle_id = vehicle.id
  AND supported.connector_type = 'CCS2'
  AND regexp_replace(upper(vehicle.connector_type), '[^A-Z0-9]', '', 'g')
      IN ('TYPE1', 'TYPE2', 'CHADEMO', 'GBT', 'BHARATDC001');

INSERT INTO vehicle_supported_connectors (vehicle_id, connector_type)
SELECT id,
       CASE
           WHEN regexp_replace(upper(connector_type), '[^A-Z0-9]', '', 'g') = 'TYPE1' THEN 'TYPE1'
           WHEN regexp_replace(upper(connector_type), '[^A-Z0-9]', '', 'g') = 'TYPE2' THEN 'TYPE2'
           WHEN regexp_replace(upper(connector_type), '[^A-Z0-9]', '', 'g') = 'CHADEMO' THEN 'CHADEMO'
           WHEN regexp_replace(upper(connector_type), '[^A-Z0-9]', '', 'g') IN ('GBT', 'BHARATDC001') THEN 'GB_T'
           ELSE 'CCS2'
       END
FROM vehicles
WHERE connector_type IS NOT NULL
ON CONFLICT DO NOTHING;
