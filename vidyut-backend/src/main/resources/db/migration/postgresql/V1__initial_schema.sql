-- ============================================================================
-- V1__initial_schema.sql
-- Baseline schema representing state immediately before V2 migrations execute.
-- ============================================================================

-- 1. Accounts
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    google_subject VARCHAR(255) UNIQUE,
    account_type VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_account_type_check CHECK (account_type IN ('INDIVIDUAL', 'COMPANY', 'ADMIN'))
);

-- 2. Account Roles
CREATE TABLE account_roles (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    PRIMARY KEY (account_id, role)
);

-- 3. EV User Profiles
CREATE TABLE ev_user_profiles (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20)
);

-- 4. Host Profiles
CREATE TABLE host_profiles (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    display_name VARCHAR(150) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone VARCHAR(20),
    address VARCHAR(500),
    bio VARCHAR(500),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_document_url VARCHAR(1000),
    identity_type VARCHAR(30),
    identity_last4 VARCHAR(4),
    verification_requested_at TIMESTAMP,
    bank_account_holder VARCHAR(150),
    bank_name VARCHAR(120),
    bank_account_last4 VARCHAR(8),
    ifsc_code VARCHAR(20),
    payout_upi VARCHAR(120),
    bank_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    push_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    auto_availability BOOLEAN NOT NULL DEFAULT FALSE,
    reputation_score DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    email_verification_code_hash VARCHAR(255),
    email_verification_expires_at TIMESTAMP
);

-- 5. Admin Permissions
CREATE TABLE admin_permissions (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    permission VARCHAR(50)
);

-- 6. Companies (pre-V23: agent controls added in V23)
CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    company_name VARCHAR(255) UNIQUE,
    registration_number VARCHAR(255) UNIQUE,
    support_email VARCHAR(255),
    support_phone VARCHAR(255),
    gst_number VARCHAR(255),
    kyc_document_url VARCHAR(255),
    business_address VARCHAR(255),
    website VARCHAR(255),
    contact_name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    push_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    verification_requested_at TIMESTAMP,
    email_verification_code_hash VARCHAR(255),
    email_verification_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Company Employees
CREATE TABLE company_employees (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    permissions VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP,
    CONSTRAINT uk_company_employee_email UNIQUE (company_id, email)
);

-- 8. Company Activity Logs
CREATE TABLE company_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    actor_account_id BIGINT NOT NULL REFERENCES accounts(id),
    action VARCHAR(40) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id BIGINT,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_company_activity_company ON company_activity_logs(company_id);
CREATE INDEX idx_company_activity_created ON company_activity_logs(created_at);

-- 9. Land Listings (pre-V3, pre-V5, pre-V19, pre-V21)
CREATE TABLE land_listings (
    id BIGSERIAL PRIMARY KEY,
    host_user_id BIGINT NOT NULL REFERENCES accounts(id),
    title VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    connector_type VARCHAR(255),
    power_kw DOUBLE PRECISION NOT NULL DEFAULT 0,
    price_per_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. Charging Stations (pre-V3, pre-V8, pre-V10, pre-V15, pre-V18, pre-V20, pre-V21)
CREATE TABLE charging_stations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    longitude DOUBLE PRECISION NOT NULL DEFAULT 0,
    price_per_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
    rating DOUBLE PRECISION NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    image_url VARCHAR(255),
    photo_urls VARCHAR(2000),
    amenities VARCHAR(1000),
    working_hours VARCHAR(255),
    weekly_schedule VARCHAR(1500),
    holiday_schedule VARCHAR(1500),
    charging_instructions VARCHAR(1000),
    auto_availability BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_disabled BOOLEAN NOT NULL DEFAULT FALSE,
    booking_slot_minutes INTEGER NOT NULL DEFAULT 60,
    queue_count INTEGER NOT NULL DEFAULT 0,
    occupancy_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    dynamic_pricing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    time_based_price_per_hour DOUBLE PRECISION,
    peak_price_per_kwh DOUBLE PRECISION,
    peak_hours VARCHAR(255),
    student_discount_percent DOUBLE PRECISION,
    corporate_price_per_kwh DOUBLE PRECISION,
    coupon_code VARCHAR(255),
    coupon_discount_percent DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    availability VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    host_user_id BIGINT REFERENCES accounts(id)
);

-- 11. Charging Connectors (pre-V25)
CREATE TABLE charging_connectors (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT REFERENCES charging_stations(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    power_kw DOUBLE PRECISION NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    charger_code VARCHAR(255) UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
    maintenance_mode BOOLEAN NOT NULL DEFAULT FALSE,
    firmware_version VARCHAR(255) DEFAULT '1.0.0',
    health_score INTEGER NOT NULL DEFAULT 100,
    last_heartbeat TIMESTAMP,
    current_power_kw DOUBLE PRECISION NOT NULL DEFAULT 0,
    session_energy_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
    session_started_at TIMESTAMP,
    fault_code VARCHAR(120),
    CONSTRAINT charging_connectors_status_check CHECK (status IN ('ONLINE', 'OFFLINE', 'CHARGING', 'MAINTENANCE', 'FAULT'))
);

-- 12. Vehicles (pre-V2 check; pre-V7, pre-V12, pre-V13)
CREATE TABLE vehicles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    make_and_model VARCHAR(255) NOT NULL,
    registration_number VARCHAR(255) NOT NULL UNIQUE,
    battery_capacity VARCHAR(255),
    connector_type VARCHAR(255),
    connection_status VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    battery_percent INTEGER,
    remaining_range_km DOUBLE PRECISION,
    charging BOOLEAN,
    bluetooth_supported BOOLEAN,
    android_auto_supported BOOLEAN,
    apple_car_play_supported BOOLEAN,
    bluetooth_device_name VARCHAR(255),
    last_charging_station VARCHAR(255),
    last_charging_address VARCHAR(255),
    last_charged_at TIMESTAMP,
    telemetry_source VARCHAR(30) NOT NULL DEFAULT 'NOT_AVAILABLE',
    telemetry_updated_at TIMESTAMP,
    CONSTRAINT vehicles_telemetry_source_check CHECK (telemetry_source IN ('MANUAL', 'CHARGING_SESSION', 'NOT_AVAILABLE'))
);

-- 13. Bookings (pre-V8, pre-V9)
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    vehicle_id BIGINT REFERENCES vehicles(id),
    seen BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(80),
    station_name VARCHAR(255),
    station_address VARCHAR(255),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_hours INTEGER NOT NULL DEFAULT 0,
    duration_minutes INTEGER NOT NULL DEFAULT 0,
    total_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    kwh_delivered DOUBLE PRECISION NOT NULL DEFAULT 0,
    cancellation_fee DOUBLE PRECISION NOT NULL DEFAULT 0,
    refund_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_booking_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- 14. Booking Waitlist
CREATE TABLE booking_waitlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    vehicle_id BIGINT REFERENCES vehicles(id),
    preferred_start_time TIMESTAMP,
    duration_minutes INTEGER NOT NULL DEFAULT 60,
    status VARCHAR(40) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 15. Host Reviews
CREATE TABLE host_reviews (
    id BIGSERIAL PRIMARY KEY,
    host_account_id BIGINT NOT NULL REFERENCES accounts(id),
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    booking_id BIGINT,
    customer_account_id BIGINT NOT NULL REFERENCES accounts(id),
    customer_name VARCHAR(150) NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(1500) NOT NULL,
    host_reply VARCHAR(1500),
    reported BOOLEAN NOT NULL DEFAULT FALSE,
    report_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_host_review_host ON host_reviews(host_account_id);
CREATE INDEX idx_host_review_station ON host_reviews(station_id);

-- 16. Charging Sessions (pre-V19)
CREATE TABLE charging_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    booking_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    vehicle_id BIGINT REFERENCES vehicles(id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    payment_status VARCHAR(50) NOT NULL DEFAULT 'DUE',
    power_kw DOUBLE PRECISION NOT NULL DEFAULT 0,
    energy_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
    cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    start_battery_percent INTEGER NOT NULL DEFAULT 0,
    current_battery_percent INTEGER NOT NULL DEFAULT 0,
    target_battery_percent INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    estimated_completion_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_charging_session_booking UNIQUE (booking_id)
);

-- 17. Payments
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    booking_id BIGINT,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    gateway_transaction_id VARCHAR(255),
    status VARCHAR(50),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 18. Payouts
CREATE TABLE payouts (
    id BIGSERIAL PRIMARY KEY,
    host_user_id BIGINT NOT NULL REFERENCES accounts(id),
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(50),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 19. EV Wallets
CREATE TABLE ev_wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES accounts(id),
    balance DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 20. Wallet Transactions
CREATE TABLE wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES ev_wallets(id) ON DELETE CASCADE,
    vehicle_id BIGINT,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    type VARCHAR(50),
    description VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 21. Vehicle Wallets
CREATE TABLE vehicle_wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    vehicle_id BIGINT NOT NULL UNIQUE REFERENCES vehicles(id) ON DELETE CASCADE,
    tag_uid VARCHAR(64) NOT NULL UNIQUE,
    balance DOUBLE PRECISION NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 22. Vehicle Wallet Transactions
CREATE TABLE vehicle_wallet_transactions (
    id BIGSERIAL PRIMARY KEY,
    wallet_id BIGINT NOT NULL REFERENCES vehicle_wallets(id) ON DELETE CASCADE,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    booking_id BIGINT,
    amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    balance_before DOUBLE PRECISION NOT NULL DEFAULT 0,
    balance_after DOUBLE PRECISION NOT NULL DEFAULT 0,
    type VARCHAR(50),
    description VARCHAR(255),
    payment_method VARCHAR(255),
    payment_reference VARCHAR(255),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 23. Vehicle Auto Recharge Rules
CREATE TABLE vehicle_auto_recharge_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    balance_threshold DOUBLE PRECISION NOT NULL DEFAULT 0,
    recharge_amount DOUBLE PRECISION NOT NULL DEFAULT 0,
    payment_method VARCHAR(80) NOT NULL,
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auto_recharge_user_vehicle UNIQUE (user_id, vehicle_id)
);

-- 24. Notifications (pre-V7)
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    title VARCHAR(255),
    message VARCHAR(255),
    type VARCHAR(50),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 25. Autopilot Trips (pre-V4, pre-V11)
CREATE TABLE autopilot_trips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES accounts(id),
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id),
    idempotency_key VARCHAR(100) NOT NULL,
    goal VARCHAR(1200) NOT NULL,
    origin VARCHAR(160) NOT NULL,
    destination VARCHAR(160) NOT NULL,
    arrival_deadline VARCHAR(20),
    optimize_for VARCHAR(30) NOT NULL,
    autonomy_mode VARCHAR(30) NOT NULL DEFAULT 'ASK_BEFORE_ACTIONS',
    starting_battery_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    current_battery_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    minimum_arrival_battery_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    maximum_charging_budget DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_drive_minutes INTEGER NOT NULL DEFAULT 0,
    total_duration_minutes INTEGER NOT NULL DEFAULT 0,
    estimated_charging_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_arrival_battery_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    active_station_id BIGINT,
    active_booking_id BIGINT,
    status VARCHAR(30) NOT NULL,
    payment_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_autopilot_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- 26. Autopilot Stops (pre-V4, pre-V10, pre-V11, pre-V12, pre-V24)
CREATE TABLE autopilot_stops (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES autopilot_trips(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    station_id BIGINT NOT NULL REFERENCES charging_stations(id),
    booking_id BIGINT,
    station_name VARCHAR(255) NOT NULL,
    station_address VARCHAR(255) NOT NULL,
    connector_type VARCHAR(30) NOT NULL,
    power_kw DOUBLE PRECISION NOT NULL DEFAULT 0,
    distance_from_origin_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    arrival_battery_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    target_battery_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    estimated_wait_minutes INTEGER NOT NULL DEFAULT 0,
    charging_minutes INTEGER NOT NULL DEFAULT 0,
    estimated_cost DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL
);

-- 27. Autopilot Actions
CREATE TABLE autopilot_actions (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES autopilot_trips(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    state VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    detail VARCHAR(1000) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
