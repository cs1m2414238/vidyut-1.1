ALTER TABLE charging_stations
    ADD COLUMN IF NOT EXISTS property_owner_account_id BIGINT,
    ADD COLUMN IF NOT EXISTS operator_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS host_partnership_id BIGINT,
    ADD COLUMN IF NOT EXISTS ownership_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS site_ownership_document_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS electricity_connection_document_url VARCHAR(1000);

-- Old Company-created stations incorrectly stored the Company account as a Host.
UPDATE charging_stations station
SET property_owner_account_id = station.host_user_id,
    operator_company_id = company.id,
    ownership_type = 'COMPANY_OWNED',
    operator_company_name = COALESCE(station.operator_company_name, company.company_name),
    property_owner_name = COALESCE(station.property_owner_name, company.company_name),
    operating_model = COALESCE(station.operating_model, 'COMPANY_OWNED_AND_OPERATED'),
    host_user_id = NULL
FROM companies company
WHERE station.host_user_id = company.account_id
  AND station.source_installation_request_id IS NULL;

-- Marketplace commissions keep the Host as property owner and the Company as operator.
UPDATE charging_stations
SET property_owner_account_id = COALESCE(property_owner_account_id, host_user_id),
    operator_company_id = COALESCE(operator_company_id, supplier_company_id),
    host_partnership_id = COALESCE(host_partnership_id, source_installation_request_id),
    ownership_type = 'HOST_PARTNERED'
WHERE host_user_id IS NOT NULL;

-- Curated/operator-only legacy stations have no Host marketplace relationship.
UPDATE charging_stations
SET operator_company_id = COALESCE(operator_company_id, supplier_company_id),
    ownership_type = COALESCE(ownership_type, 'COMPANY_OWNED')
WHERE ownership_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_station_operator_company ON charging_stations(operator_company_id);
CREATE INDEX IF NOT EXISTS idx_station_property_owner ON charging_stations(property_owner_account_id);
