ALTER TABLE charging_connectors
    DROP CONSTRAINT IF EXISTS charging_connectors_status_check;

ALTER TABLE charging_connectors
    ADD CONSTRAINT charging_connectors_status_check CHECK (status IN (
        'ONLINE',
        'OFFLINE',
        'CHARGING',
        'MAINTENANCE',
        'FAULT',
        'SUSPECTED_FAULT'
    ));
