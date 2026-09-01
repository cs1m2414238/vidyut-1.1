ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deep_link VARCHAR(500);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS critical BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_notification_preference_user_type UNIQUE (user_id, type)
);

CREATE TABLE IF NOT EXISTS push_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_push_device_token UNIQUE (token)
);

ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS bluetooth_device_id VARCHAR(255);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS bluetooth_service_uuid VARCHAR(255);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS bt_session_control_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS bt_simulator_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_notification_user_unread ON notifications (user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_push_device_user ON push_devices (user_id, enabled);
