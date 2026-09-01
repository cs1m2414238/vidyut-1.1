BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM accounts
        WHERE lower(email) = 'contactpriyanshusharma6281@gmail.com'
          AND account_type = 'COMPANY'
    ) THEN
        RAISE EXCEPTION 'Expected TATA Company account was not found';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM accounts
        WHERE lower(email) = 'hgj13918@gmail.com'
          AND account_type = 'INDIVIDUAL'
    ) THEN
        RAISE EXCEPTION 'Expected prince Host account was not found';
    END IF;
END $$;

UPDATE accounts
SET email_verified = TRUE,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE lower(email) IN (
    'contactpriyanshusharma6281@gmail.com',
    'hgj13918@gmail.com'
);

UPDATE companies
SET company_name = 'TATA',
    registration_number = 'U99999UP2026PTC062811',
    support_email = 'contactpriyanshusharma6281@gmail.com',
    support_phone = '+919000000000',
    gst_number = '09AAAAA0000A1Z5',
    kyc_document_url = 'https://demo.vidyut.local/documents/tata/incorporation.pdf',
    business_address = 'TATA Vidyut Demo Office, Gomti Nagar, Lucknow, Uttar Pradesh 226010',
    website = 'https://demo.tata.vidyut.local',
    contact_name = 'Priyanshu Sharma',
    active = TRUE,
    verification_status = 'VERIFIED',
    email_notifications = TRUE,
    push_notifications = TRUE,
    timezone = 'Asia/Kolkata',
    verification_requested_at = COALESCE(verification_requested_at, CURRENT_TIMESTAMP),
    email_verification_code_hash = NULL,
    email_verification_expires_at = NULL
WHERE account_id = (
    SELECT id FROM accounts
    WHERE lower(email) = 'contactpriyanshusharma6281@gmail.com'
);

INSERT INTO company_verifications (
    company_id, legal_name, cin_llpin, gstin, pan_hash, pan_last4, udyam_number,
    registered_address, website, representative_name, representative_work_email,
    representative_phone, representative_designation, authorization_proof_url,
    bank_account_holder, bank_name, bank_account_last4, ifsc_code,
    cancelled_cheque_url, incorporation_document_url, gst_certificate_url,
    charger_catalogue_url, compliance_document_url, business_identity_verified,
    representative_verified, bank_verified, charger_documents_verified, status,
    trust_level, admin_review_note, rejection_reason, submitted_at, reviewed_at,
    updated_at
)
SELECT
    c.id,
    'TATA (Vidyut Demo)',
    'U99999UP2026PTC062811',
    '09AAAAA0000A1Z5',
    repeat('0', 64),
    '6281',
    'UDYAM-UP-50-0000628',
    'TATA Vidyut Demo Office, Gomti Nagar, Lucknow, Uttar Pradesh 226010',
    'https://demo.tata.vidyut.local',
    'Priyanshu Sharma',
    'contactpriyanshusharma6281@gmail.com',
    '+919000000000',
    'Demo Company Administrator',
    'https://demo.vidyut.local/documents/tata/authorization.pdf',
    'TATA Vidyut Demo',
    'Demo Bank',
    '6281',
    'HDFC0000001',
    'https://demo.vidyut.local/documents/tata/cancelled-cheque.pdf',
    'https://demo.vidyut.local/documents/tata/incorporation.pdf',
    'https://demo.vidyut.local/documents/tata/gst-certificate.pdf',
    'https://demo.vidyut.local/documents/tata/charger-catalogue.pdf',
    'https://demo.vidyut.local/documents/tata/compliance.pdf',
    TRUE, TRUE, TRUE, TRUE,
    'VERIFIED',
    'VIDYUT_VERIFIED',
    'Approved mock profile for the local TATA-to-prince workflow.',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM companies c
JOIN accounts a ON a.id = c.account_id
WHERE lower(a.email) = 'contactpriyanshusharma6281@gmail.com'
ON CONFLICT (company_id) DO UPDATE SET
    legal_name = EXCLUDED.legal_name,
    cin_llpin = EXCLUDED.cin_llpin,
    gstin = EXCLUDED.gstin,
    pan_hash = EXCLUDED.pan_hash,
    pan_last4 = EXCLUDED.pan_last4,
    udyam_number = EXCLUDED.udyam_number,
    registered_address = EXCLUDED.registered_address,
    website = EXCLUDED.website,
    representative_name = EXCLUDED.representative_name,
    representative_work_email = EXCLUDED.representative_work_email,
    representative_phone = EXCLUDED.representative_phone,
    representative_designation = EXCLUDED.representative_designation,
    authorization_proof_url = EXCLUDED.authorization_proof_url,
    bank_account_holder = EXCLUDED.bank_account_holder,
    bank_name = EXCLUDED.bank_name,
    bank_account_last4 = EXCLUDED.bank_account_last4,
    ifsc_code = EXCLUDED.ifsc_code,
    cancelled_cheque_url = EXCLUDED.cancelled_cheque_url,
    incorporation_document_url = EXCLUDED.incorporation_document_url,
    gst_certificate_url = EXCLUDED.gst_certificate_url,
    charger_catalogue_url = EXCLUDED.charger_catalogue_url,
    compliance_document_url = EXCLUDED.compliance_document_url,
    business_identity_verified = TRUE,
    representative_verified = TRUE,
    bank_verified = TRUE,
    charger_documents_verified = TRUE,
    status = 'VERIFIED',
    trust_level = 'VIDYUT_VERIFIED',
    admin_review_note = EXCLUDED.admin_review_note,
    rejection_reason = NULL,
    submitted_at = COALESCE(company_verifications.submitted_at, EXCLUDED.submitted_at),
    reviewed_at = EXCLUDED.reviewed_at,
    updated_at = EXCLUDED.updated_at;

UPDATE host_profiles
SET display_name = 'prince',
    verified = TRUE,
    phone = '+919000000001',
    address = 'Amausi Industrial Area, Lucknow, Uttar Pradesh 226009',
    bio = 'Verified demo Host for the TATA charger installation and routing workflow.',
    verification_status = 'VERIFIED',
    kyc_document_url = 'https://demo.vidyut.local/documents/prince/host-kyc.pdf',
    identity_type = 'PAN',
    identity_last4 = 'H918',
    verification_requested_at = COALESCE(verification_requested_at, CURRENT_TIMESTAMP),
    bank_account_holder = 'Prince',
    bank_name = 'Demo Bank',
    bank_account_last4 = '3918',
    ifsc_code = 'HDFC0000001',
    payout_upi = 'prince@demo',
    bank_verified = TRUE,
    email_notifications = TRUE,
    push_notifications = TRUE,
    auto_availability = TRUE,
    reputation_score = 5.0,
    email_verification_code_hash = NULL,
    email_verification_expires_at = NULL
WHERE account_id = (
    SELECT id FROM accounts
    WHERE lower(email) = 'hgj13918@gmail.com'
);

INSERT INTO company_service_areas (
    company_id, city, state, pincode, latitude, longitude, radius_km,
    installation_available, maintenance_available, survey_fee,
    typical_installation_days, active
)
SELECT c.id, 'Lucknow', 'Uttar Pradesh', '226009', 26.7606, 80.8893, 120,
       TRUE, TRUE, 2500, 21, TRUE
FROM companies c
JOIN accounts a ON a.id = c.account_id
WHERE lower(a.email) = 'contactpriyanshusharma6281@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM company_service_areas area
      WHERE area.company_id = c.id
        AND lower(area.city) = 'lucknow'
        AND area.pincode = '226009'
  );

INSERT INTO charger_products (
    company_id, model_name, manufacturer, current_type, connector_type,
    power_kw, equipment_price, installation_price, warranty_months,
    amc_available, certifications, description, image_url,
    compliance_document_url, approval_status, admin_review_note, active
)
SELECT c.id,
       'TATA Power EZ Charge 60 DC (Demo)',
       'TATA (Demo)',
       'DC',
       'CCS2',
       60,
       950000,
       150000,
       36,
       TRUE,
       'Mock ARAI, CE and OCPP 1.6J',
       'Approved demo rapid charger for the Prince Amausi land workflow.',
       'https://demo.vidyut.local/images/tata-60kw-dc.png',
       'https://demo.vidyut.local/documents/tata/compliance.pdf',
       'APPROVED',
       'Approved mock product for local workflow testing.',
       TRUE
FROM companies c
JOIN accounts a ON a.id = c.account_id
WHERE lower(a.email) = 'contactpriyanshusharma6281@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM charger_products product
      WHERE product.company_id = c.id
        AND product.model_name = 'TATA Power EZ Charge 60 DC (Demo)'
  );

INSERT INTO charger_product_business_models (product_id, business_model)
SELECT product.id, model.business_model
FROM charger_products product
JOIN companies c ON c.id = product.company_id
JOIN accounts a ON a.id = c.account_id
CROSS JOIN (VALUES ('PURCHASE'), ('REVENUE_SHARE'), ('COMPANY_OWNED')) AS model(business_model)
WHERE lower(a.email) = 'contactpriyanshusharma6281@gmail.com'
  AND product.model_name = 'TATA Power EZ Charge 60 DC (Demo)'
ON CONFLICT (product_id, business_model) DO NOTHING;

INSERT INTO land_listings (
    host_user_id, title, address, city, state, pincode, latitude, longitude,
    connector_type, power_kw, price_per_kwh, property_type,
    available_parking_bays, power_phase, available_load_kw, operating_hours,
    ownership_type, preferred_connector_type, preferred_power_kw, photo_urls,
    ownership_document_url, admin_review_note, discoverable, status
)
SELECT a.id,
       'Prince Amausi Highway EV Site (Demo)',
       'Amausi Industrial Area, near Lucknow Airport, Lucknow, Uttar Pradesh 226009',
       'Lucknow',
       'Uttar Pradesh',
       '226009',
       26.7606,
       80.8893,
       'CCS2',
       60,
       14,
       'HIGHWAY',
       8,
       'THREE_PHASE',
       160,
       '24x7',
       'OWNED',
       'CCS2',
       60,
       'https://demo.vidyut.local/images/prince-amausi-site.jpg',
       'https://demo.vidyut.local/documents/prince/land-ownership.pdf',
       'Approved mock property for local workflow testing.',
       TRUE,
       'ACTIVE'
FROM accounts a
WHERE lower(a.email) = 'hgj13918@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM land_listings listing
      WHERE listing.host_user_id = a.id
        AND listing.title = 'Prince Amausi Highway EV Site (Demo)'
  );

COMMIT;
