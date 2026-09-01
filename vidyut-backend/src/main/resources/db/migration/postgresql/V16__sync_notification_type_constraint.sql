ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check CHECK (type IN (
        'CHARGING_COMPLETED',
        'BOOKING_CONFIRMED',
        'BOOKING_REMINDER',
        'WALLET_CREDIT',
        'WALLET_LOW_BALANCE',
        'AUTO_RECHARGE',
        'SYSTEM_ALERT',
        'NEW_BOOKING',
        'BOOKING_CANCELLED',
        'CHARGING_STARTED',
        'STATION_FULL_DIVERSION',
        'BT_CHARGE_COMPLETED',
        'WAITLIST_AVAILABLE',
        'AGENT_REPLAN',
        'PAYMENT_RECEIVED',
        'FAULT_ALERT',
        'LOW_UPTIME_ALERT'
    ));
