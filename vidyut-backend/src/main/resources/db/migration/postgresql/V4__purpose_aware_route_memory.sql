ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS trip_purpose VARCHAR(30) DEFAULT 'GENERAL';
ALTER TABLE autopilot_trips ADD COLUMN IF NOT EXISTS memory_summary VARCHAR(1000);
ALTER TABLE autopilot_stops ADD COLUMN IF NOT EXISTS selection_reason VARCHAR(1000);

CREATE TABLE IF NOT EXISTS route_experiences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    trip_id BIGINT,
    station_id BIGINT,
    origin VARCHAR(160) NOT NULL,
    destination VARCHAR(160) NOT NULL,
    origin_key VARCHAR(120) NOT NULL,
    destination_key VARCHAR(120) NOT NULL,
    outcome VARCHAR(30) NOT NULL CHECK (outcome IN (
        'SUCCESS','CHARGER_FAULT','EXCESS_WAIT','ACCESS_ISSUE','PAYMENT_ISSUE','USER_REPORTED'
    )),
    detail VARCHAR(1200),
    rating INTEGER,
    delay_minutes INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_route_experience_lookup
    ON route_experiences(origin_key, destination_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_route_experience_station
    ON route_experiences(station_id, outcome);
