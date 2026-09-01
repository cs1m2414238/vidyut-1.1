ALTER TABLE charging_stations
    ADD COLUMN IF NOT EXISTS property_owner_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS operator_company_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS equipment_owner_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS operating_model VARCHAR(80),
    ADD COLUMN IF NOT EXISTS solar_provider_name VARCHAR(160);
