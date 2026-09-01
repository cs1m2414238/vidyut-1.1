ALTER TABLE charging_sessions
    ADD COLUMN IF NOT EXISTS connector_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_charging_session_connector_status
    ON charging_sessions (connector_id, status);

ALTER TABLE land_listings
    ADD COLUMN IF NOT EXISTS electricity_document_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS video_verification_url VARCHAR(1000);
