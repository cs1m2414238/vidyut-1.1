-- Additive only. Legacy journeys have no verified current position and must refresh
-- telemetry before recovery; never backfill a guessed position from a failed stop.
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS current_latitude DOUBLE PRECISION;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS current_longitude DOUBLE PRECISION;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS position_recorded_at TIMESTAMP;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS position_source VARCHAR(40);
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS distance_travelled_km DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS elapsed_drive_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS navigation_route_json TEXT;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS route_start_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS recovery_json TEXT;
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS arrival_deadline_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS connector_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_booking_connector_window ON bookings(connector_id, start_time, end_time);
