ALTER TABLE charging_connectors ADD COLUMN IF NOT EXISTS fault_reason VARCHAR(500);
ALTER TABLE charging_connectors ADD COLUMN IF NOT EXISTS status_source VARCHAR(60);
ALTER TABLE charging_connectors ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP;
ALTER TABLE autopilot_stops ADD COLUMN IF NOT EXISTS connector_id BIGINT;
ALTER TABLE autopilot_stops ADD COLUMN IF NOT EXISTS charger_code VARCHAR(255);

-- Only backfill old plans when their stored type and power identify one connector.
UPDATE autopilot_stops s SET connector_id = c.id, charger_code = c.charger_code
FROM charging_connectors c
WHERE s.connector_id IS NULL AND s.station_id = c.station_id
  AND s.connector_type = c.type AND ABS(s.power_kw - c.power_kw) < 0.01
  AND (SELECT COUNT(*) FROM charging_connectors other
       WHERE other.station_id = s.station_id AND other.type = s.connector_type
       AND ABS(other.power_kw - s.power_kw) < 0.01) = 1;

CREATE INDEX IF NOT EXISTS idx_autopilot_stop_connector_status ON autopilot_stops(connector_id, status);
