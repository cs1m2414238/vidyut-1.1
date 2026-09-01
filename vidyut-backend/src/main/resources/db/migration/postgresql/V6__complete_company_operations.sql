CREATE TABLE IF NOT EXISTS company_maintenance_tickets (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    charger_id BIGINT NOT NULL REFERENCES charging_connectors(id),
    charger_code VARCHAR(120) NOT NULL,
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    station_name VARCHAR(180) NOT NULL,
    city VARCHAR(120),
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    issue VARCHAR(1500) NOT NULL,
    assigned_to VARCHAR(150),
    resolution_note VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT company_maintenance_priority_check
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT company_maintenance_status_check
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_company_maintenance_company_updated
    ON company_maintenance_tickets(company_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_company_maintenance_charger
    ON company_maintenance_tickets(charger_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_active_maintenance_charger
    ON company_maintenance_tickets(company_id, charger_id)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
