ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS city VARCHAR(120);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS state VARCHAR(120);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS pincode VARCHAR(12);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS property_type VARCHAR(40) DEFAULT 'OTHER';
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS available_parking_bays INTEGER DEFAULT 1;
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS power_phase VARCHAR(30) DEFAULT 'NOT_SURE';
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS available_load_kw DOUBLE PRECISION DEFAULT 0;
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS operating_hours VARCHAR(255);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS ownership_type VARCHAR(30) DEFAULT 'OWNED';
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS preferred_connector_type VARCHAR(40);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS preferred_power_kw DOUBLE PRECISION DEFAULT 0;
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS photo_urls VARCHAR(2000);
ALTER TABLE land_listings ADD COLUMN IF NOT EXISTS discoverable BOOLEAN DEFAULT TRUE;

ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS supplier_company_id BIGINT;
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS source_installation_request_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_station_source_installation
    ON charging_stations(source_installation_request_id)
    WHERE source_installation_request_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS company_service_areas (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(12),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    radius_km DOUBLE PRECISION NOT NULL DEFAULT 50,
    installation_available BOOLEAN NOT NULL DEFAULT TRUE,
    maintenance_available BOOLEAN NOT NULL DEFAULT TRUE,
    survey_fee DOUBLE PRECISION NOT NULL DEFAULT 0,
    typical_installation_days INTEGER NOT NULL DEFAULT 14,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS charger_products (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    model_name VARCHAR(140) NOT NULL,
    manufacturer VARCHAR(140) NOT NULL,
    current_type VARCHAR(10) NOT NULL CHECK (current_type IN ('AC','DC')),
    connector_type VARCHAR(20) NOT NULL CHECK (connector_type IN ('TYPE1','TYPE2','CCS2','CHADEMO','GB_T')),
    power_kw DOUBLE PRECISION NOT NULL,
    equipment_price DOUBLE PRECISION NOT NULL DEFAULT 0,
    installation_price DOUBLE PRECISION NOT NULL DEFAULT 0,
    warranty_months INTEGER NOT NULL DEFAULT 0,
    amc_available BOOLEAN NOT NULL DEFAULT FALSE,
    certifications VARCHAR(300),
    description VARCHAR(1500),
    image_url VARCHAR(600),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS charger_product_business_models (
    product_id BIGINT NOT NULL REFERENCES charger_products(id) ON DELETE CASCADE,
    business_model VARCHAR(30) NOT NULL CHECK (business_model IN ('PURCHASE','LEASE','REVENUE_SHARE','COMPANY_OWNED')),
    PRIMARY KEY (product_id, business_model)
);

CREATE TABLE IF NOT EXISTS installation_requests (
    id BIGSERIAL PRIMARY KEY,
    host_user_id BIGINT NOT NULL REFERENCES accounts(id),
    land_listing_id BIGINT NOT NULL REFERENCES land_listings(id),
    company_id BIGINT NOT NULL REFERENCES companies(id),
    charger_product_id BIGINT NOT NULL REFERENCES charger_products(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    business_model VARCHAR(30) NOT NULL CHECK (business_model IN ('PURCHASE','LEASE','REVENUE_SHARE','COMPANY_OWNED')),
    budget DOUBLE PRECISION,
    target_installation_date DATE,
    host_message VARCHAR(1500),
    company_note VARCHAR(1500),
    scheduled_survey_at DATE,
    scheduled_installation_at DATE,
    station_id BIGINT,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS installation_proposals (
    id BIGSERIAL PRIMARY KEY,
    installation_request_id BIGINT NOT NULL UNIQUE REFERENCES installation_requests(id) ON DELETE CASCADE,
    equipment_total DOUBLE PRECISION NOT NULL DEFAULT 0,
    installation_total DOUBLE PRECISION NOT NULL DEFAULT 0,
    monthly_lease DOUBLE PRECISION,
    host_revenue_share_percent DOUBLE PRECISION,
    company_revenue_share_percent DOUBLE PRECISION,
    valid_until DATE,
    estimated_installation_days INTEGER NOT NULL DEFAULT 14,
    terms VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS installation_status_history (
    id BIGSERIAL PRIMARY KEY,
    installation_request_id BIGINT NOT NULL REFERENCES installation_requests(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL,
    actor_account_id BIGINT NOT NULL REFERENCES accounts(id),
    note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS company_property_interests (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    land_listing_id BIGINT NOT NULL REFERENCES land_listings(id),
    message VARCHAR(1200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','DECLINED','WITHDRAWN')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_property_interest UNIQUE (company_id, land_listing_id)
);

CREATE INDEX IF NOT EXISTS idx_service_area_company ON company_service_areas(company_id);
CREATE INDEX IF NOT EXISTS idx_charger_product_company ON charger_products(company_id);
CREATE INDEX IF NOT EXISTS idx_installation_host ON installation_requests(host_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_installation_company ON installation_requests(company_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_interest_property ON company_property_interests(land_listing_id);
