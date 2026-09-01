CREATE TABLE IF NOT EXISTS company_verifications (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL UNIQUE REFERENCES companies(id),
    legal_name VARCHAR(180), cin_llpin VARCHAR(40), gstin VARCHAR(20),
    pan_hash VARCHAR(64), pan_last4 VARCHAR(4), udyam_number VARCHAR(30),
    registered_address VARCHAR(600), website VARCHAR(300),
    representative_name VARCHAR(150), representative_work_email VARCHAR(255),
    representative_phone VARCHAR(20), representative_designation VARCHAR(100),
    authorization_proof_url VARCHAR(1000),
    bank_account_holder VARCHAR(180), bank_name VARCHAR(120), bank_account_last4 VARCHAR(4),
    ifsc_code VARCHAR(20), cancelled_cheque_url VARCHAR(1000),
    incorporation_document_url VARCHAR(1000), gst_certificate_url VARCHAR(1000),
    charger_catalogue_url VARCHAR(1000), compliance_document_url VARCHAR(1000),
    business_identity_verified BOOLEAN NOT NULL DEFAULT FALSE,
    representative_verified BOOLEAN NOT NULL DEFAULT FALSE,
    bank_verified BOOLEAN NOT NULL DEFAULT FALSE,
    charger_documents_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    trust_level VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED',
    admin_review_note VARCHAR(1200), rejection_reason VARCHAR(1200),
    reviewed_by_admin_id BIGINT, submitted_at TIMESTAMP, reviewed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO company_verifications (
    company_id, legal_name, cin_llpin, gstin, registered_address, website,
    incorporation_document_url, business_identity_verified, representative_verified,
    bank_verified, charger_documents_verified, status, trust_level, submitted_at
)
SELECT id, company_name, registration_number, gst_number, business_address, website,
       kyc_document_url,
       CASE WHEN verification_status = 'VERIFIED' THEN TRUE ELSE FALSE END,
       CASE WHEN verification_status = 'VERIFIED' THEN TRUE ELSE FALSE END,
       CASE WHEN verification_status = 'VERIFIED' THEN TRUE ELSE FALSE END,
       CASE WHEN verification_status = 'VERIFIED' THEN TRUE ELSE FALSE END,
       CASE WHEN verification_status = 'VERIFIED' THEN 'VERIFIED' ELSE 'NOT_STARTED' END,
       CASE WHEN verification_status = 'VERIFIED' THEN 'VIDYUT_VERIFIED' ELSE 'UNVERIFIED' END,
       verification_requested_at
FROM companies
ON CONFLICT (company_id) DO NOTHING;

ALTER TABLE charger_products ADD COLUMN IF NOT EXISTS compliance_document_url VARCHAR(1000);
ALTER TABLE charger_products ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30);
ALTER TABLE charger_products ADD COLUMN IF NOT EXISTS admin_review_note VARCHAR(800);
UPDATE charger_products SET approval_status = 'APPROVED' WHERE approval_status IS NULL;
ALTER TABLE charger_products ALTER COLUMN approval_status SET DEFAULT 'PENDING_REVIEW';
ALTER TABLE charger_products ALTER COLUMN approval_status SET NOT NULL;

ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS ownership_document_url VARCHAR(1000);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS admin_review_note VARCHAR(800);

CREATE TABLE IF NOT EXISTS admin_accounts (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id),
    display_name VARCHAR(150) NOT NULL,
    admin_role VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_account_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(80),
    summary VARCHAR(1200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created ON admin_audit_logs(created_at DESC);

CREATE TABLE IF NOT EXISTS admin_announcements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    audience VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_admin_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
