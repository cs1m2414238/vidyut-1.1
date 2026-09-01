-- Local development fixture only. This intentionally resets Prince's password
-- so the mocked verified Host can complete the real JWT login flow.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;

DO $$
DECLARE
    prince_account_id BIGINT;
BEGIN
    SELECT id
    INTO prince_account_id
    FROM accounts
    WHERE lower(email) = 'hgj13918@gmail.com'
      AND account_type = 'INDIVIDUAL';

    IF prince_account_id IS NULL THEN
        RAISE EXCEPTION 'Prince account hgj13918@gmail.com was not found as an INDIVIDUAL account';
    END IF;

    UPDATE accounts
    SET email_verified = TRUE,
        enabled = TRUE,
        password_hash = crypt('PrinceHost123!', gen_salt('bf', 10)),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = prince_account_id;

    INSERT INTO account_roles (account_id, role)
    VALUES (prince_account_id, 'ROLE_HOST')
    ON CONFLICT (account_id, role) DO NOTHING;

    INSERT INTO host_profiles (
        account_id,
        display_name,
        verified,
        phone,
        address,
        bio,
        verification_status,
        kyc_document_url,
        identity_type,
        identity_last4,
        verification_requested_at,
        bank_account_holder,
        bank_name,
        bank_account_last4,
        ifsc_code,
        payout_upi,
        bank_verified,
        email_notifications,
        push_notifications,
        auto_availability,
        reputation_score,
        email_verification_code_hash,
        email_verification_expires_at
    ) VALUES (
        prince_account_id,
        'prince',
        TRUE,
        '9000000001',
        'Amausi Industrial Area, Lucknow, Uttar Pradesh 226009',
        'Verified demo Host for the TATA charger installation and routing workflow.',
        'VERIFIED',
        'https://demo.vidyut.local/documents/prince/host-kyc.pdf',
        'PAN',
        'H918',
        CURRENT_TIMESTAMP,
        'Prince',
        'Demo Bank',
        '3918',
        'HDFC0000001',
        'prince@demo',
        TRUE,
        TRUE,
        TRUE,
        TRUE,
        5.0,
        NULL,
        NULL
    )
    ON CONFLICT (account_id) DO UPDATE SET
        display_name = EXCLUDED.display_name,
        verified = TRUE,
        phone = EXCLUDED.phone,
        address = EXCLUDED.address,
        bio = EXCLUDED.bio,
        verification_status = 'VERIFIED',
        kyc_document_url = EXCLUDED.kyc_document_url,
        identity_type = EXCLUDED.identity_type,
        identity_last4 = EXCLUDED.identity_last4,
        verification_requested_at = COALESCE(host_profiles.verification_requested_at, EXCLUDED.verification_requested_at),
        bank_account_holder = EXCLUDED.bank_account_holder,
        bank_name = EXCLUDED.bank_name,
        bank_account_last4 = EXCLUDED.bank_account_last4,
        ifsc_code = EXCLUDED.ifsc_code,
        payout_upi = EXCLUDED.payout_upi,
        bank_verified = TRUE,
        email_notifications = TRUE,
        push_notifications = TRUE,
        auto_availability = TRUE,
        reputation_score = EXCLUDED.reputation_score,
        email_verification_code_hash = NULL,
        email_verification_expires_at = NULL;
END $$;

COMMIT;

SELECT
    a.id AS account_id,
    a.email,
    a.email_verified,
    a.enabled,
    hp.display_name,
    hp.verification_status,
    hp.verified,
    hp.phone,
    hp.address,
    hp.identity_type,
    hp.identity_last4,
    hp.bank_verified,
    hp.auto_availability,
    hp.reputation_score
FROM accounts a
JOIN host_profiles hp ON hp.account_id = a.id
WHERE lower(a.email) = 'hgj13918@gmail.com';
