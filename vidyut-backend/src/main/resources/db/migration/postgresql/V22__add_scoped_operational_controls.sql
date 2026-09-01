CREATE TABLE IF NOT EXISTS account_operational_controls (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    restrict_new_bookings BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_payments BOOLEAN NOT NULL DEFAULT FALSE,
    require_user_verification BOOLEAN NOT NULL DEFAULT FALSE,
    access_restricted_until TIMESTAMP,
    pause_new_listings BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_payouts BOOLEAN NOT NULL DEFAULT FALSE,
    suspend_new_partnerships BOOLEAN NOT NULL DEFAULT FALSE,
    require_site_reverification BOOLEAN NOT NULL DEFAULT FALSE,
    pause_company_bookings BOOLEAN NOT NULL DEFAULT FALSE,
    disable_station_publishing BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_settlements BOOLEAN NOT NULL DEFAULT FALSE,
    suspend_marketplace_access BOOLEAN NOT NULL DEFAULT FALSE,
    require_compliance_review BOOLEAN NOT NULL DEFAULT FALSE,
    warning_message VARCHAR(1200),
    reason VARCHAR(1200),
    updated_by_admin_id BIGINT REFERENCES admin_accounts(account_id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_operational_controls_updated
    ON account_operational_controls(updated_at DESC);
