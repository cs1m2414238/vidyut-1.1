ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS outlet_partner BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS outlet_institution_name VARCHAR(255);
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS outlet_email_domains VARCHAR(1000);
ALTER TABLE charging_stations ADD COLUMN IF NOT EXISTS outlet_id_verification_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS outlet_id BIGINT;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS outlet_tier_name VARCHAR(255);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS applied_rate_per_kwh DOUBLE PRECISION;

CREATE TABLE IF NOT EXISTS outlet_pricing_tiers (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    rate_per_kwh DOUBLE PRECISION NOT NULL,
    eligibility VARCHAR(30) NOT NULL,
    email_domain VARCHAR(255),
    priority INTEGER NOT NULL DEFAULT 100,
    CONSTRAINT fk_outlet_tier_station FOREIGN KEY (station_id) REFERENCES charging_stations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS outlet_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    document_uri VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    approved_tier_id BIGINT,
    review_note VARCHAR(500),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_outlet_verification_user_station UNIQUE (user_id, station_id),
    CONSTRAINT fk_outlet_verification_station FOREIGN KEY (station_id) REFERENCES charging_stations(id) ON DELETE CASCADE,
    CONSTRAINT fk_outlet_verification_tier FOREIGN KEY (approved_tier_id) REFERENCES outlet_pricing_tiers(id)
);

CREATE INDEX IF NOT EXISTS idx_outlet_tier_station ON outlet_pricing_tiers (station_id, priority);
CREATE INDEX IF NOT EXISTS idx_outlet_verification_status ON outlet_verifications (status, updated_at);

WITH target AS (
    SELECT id FROM charging_stations ORDER BY id LIMIT 1
)
UPDATE charging_stations
SET outlet_partner = TRUE,
    outlet_institution_name = COALESCE(outlet_institution_name, 'PSIT Campus Charging Hub'),
    outlet_email_domains = COALESCE(outlet_email_domains, 'psit.ac.in'),
    outlet_id_verification_required = FALSE
WHERE id IN (SELECT id FROM target);

INSERT INTO outlet_pricing_tiers (station_id, name, rate_per_kwh, eligibility, email_domain, priority)
SELECT station.id, 'Faculty', 4.00, 'EMAIL_DOMAIN', 'psit.ac.in', 10
FROM charging_stations station
WHERE station.outlet_partner = TRUE
  AND NOT EXISTS (SELECT 1 FROM outlet_pricing_tiers tier WHERE tier.station_id = station.id AND tier.name = 'Faculty');

INSERT INTO outlet_pricing_tiers (station_id, name, rate_per_kwh, eligibility, email_domain, priority)
SELECT station.id, 'Student', 6.00, 'VERIFIED_ID', NULL, 20
FROM charging_stations station
WHERE station.outlet_partner = TRUE
  AND NOT EXISTS (SELECT 1 FROM outlet_pricing_tiers tier WHERE tier.station_id = station.id AND tier.name = 'Student');

INSERT INTO outlet_pricing_tiers (station_id, name, rate_per_kwh, eligibility, email_domain, priority)
SELECT station.id, 'Visitor', 9.00, 'VISITOR', NULL, 100
FROM charging_stations station
WHERE station.outlet_partner = TRUE
  AND NOT EXISTS (SELECT 1 FROM outlet_pricing_tiers tier WHERE tier.station_id = station.id AND tier.name = 'Visitor');
