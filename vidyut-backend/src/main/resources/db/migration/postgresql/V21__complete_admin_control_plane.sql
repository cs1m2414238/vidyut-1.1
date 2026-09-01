ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS previous_value VARCHAR(2000);
ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS new_value VARCHAR(2000);
ALTER TABLE admin_audit_logs ADD COLUMN IF NOT EXISTS reason VARCHAR(1200);

ALTER TABLE admin_announcements ADD COLUMN IF NOT EXISTS target_state VARCHAR(120);
ALTER TABLE admin_announcements ADD COLUMN IF NOT EXISTS target_city VARCHAR(120);
ALTER TABLE admin_announcements ADD COLUMN IF NOT EXISTS target_account_id BIGINT;

ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS verification_stage VARCHAR(40);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS verification_risk VARCHAR(20);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS verification_method VARCHAR(60);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS video_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS physical_inspection_status VARCHAR(40);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS inspection_scheduled_at TIMESTAMP;
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS inspection_note VARCHAR(1200);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS property_score INTEGER;
UPDATE land_listings
SET verification_stage = CASE
    WHEN status IN ('APPROVED', 'ACTIVE') THEN 'PUBLISHED'
    WHEN status = 'REJECTED' THEN 'REJECTED'
    ELSE 'SUBMITTED'
END
WHERE verification_stage IS NULL;
ALTER TABLE land_listings ALTER COLUMN verification_stage SET DEFAULT 'SUBMITTED';
ALTER TABLE land_listings ALTER COLUMN verification_stage SET NOT NULL;

ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS verification_stage VARCHAR(40);
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS admin_review_note VARCHAR(1200);
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS reviewed_by_admin_id BIGINT;
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
UPDATE charging_stations SET verification_stage = 'LIVE' WHERE verification_stage IS NULL;
ALTER TABLE charging_stations ALTER COLUMN verification_stage SET DEFAULT 'SUBMITTED';
ALTER TABLE charging_stations ALTER COLUMN verification_stage SET NOT NULL;

CREATE TABLE IF NOT EXISTS network_incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_code VARCHAR(40) NOT NULL UNIQUE,
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    connector_id BIGINT REFERENCES charging_connectors(id),
    station_name VARCHAR(180) NOT NULL,
    operator_company_id BIGINT,
    operator_company_name VARCHAR(180),
    host_account_id BIGINT,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    fault_code VARCHAR(120),
    description VARCHAR(1500) NOT NULL,
    affected_bookings INTEGER NOT NULL DEFAULT 0,
    users_rerouted INTEGER NOT NULL DEFAULT 0,
    approvals_required INTEGER NOT NULL DEFAULT 0,
    manual_interventions INTEGER NOT NULL DEFAULT 0,
    estimated_downtime_minutes INTEGER NOT NULL DEFAULT 0,
    maintenance_ticket_id BIGINT,
    resolution_note VARCHAR(1500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_network_incident_status ON network_incidents(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_network_incident_station ON network_incidents(station_id, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_support_cases (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    account_type VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    subject VARCHAR(180) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    assigned_admin_id BIGINT,
    evidence_note VARCHAR(1500),
    resolution_note VARCHAR(1500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_support_status ON admin_support_cases(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_support_account ON admin_support_cases(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS admin_green_schemes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(220) NOT NULL,
    authority VARCHAR(180) NOT NULL,
    scheme_type VARCHAR(50) NOT NULL,
    states VARCHAR(500),
    source_url VARCHAR(1000) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    valid_from DATE,
    valid_until DATE,
    last_verified_at TIMESTAMP,
    created_by_admin_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_green_scheme_status ON admin_green_schemes(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS admin_settlements (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL UNIQUE REFERENCES payments(id),
    booking_id BIGINT,
    station_id BIGINT,
    station_name VARCHAR(180),
    ownership_type VARCHAR(40),
    gross_amount DOUBLE PRECISION NOT NULL,
    platform_amount DOUBLE PRECISION NOT NULL,
    company_amount DOUBLE PRECISION NOT NULL,
    host_amount DOUBLE PRECISION NOT NULL,
    taxes_amount DOUBLE PRECISION NOT NULL,
    status VARCHAR(30) NOT NULL,
    dispute_note VARCHAR(1500),
    processed_by_admin_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_settlement_status ON admin_settlements(status, updated_at DESC);
