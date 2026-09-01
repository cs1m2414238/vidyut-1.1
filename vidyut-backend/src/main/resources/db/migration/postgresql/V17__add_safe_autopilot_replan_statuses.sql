ALTER TABLE autopilot_trips
    DROP CONSTRAINT IF EXISTS autopilot_trips_status_check;

ALTER TABLE autopilot_trips
    ADD CONSTRAINT autopilot_trips_status_check CHECK (status IN (
        'RESERVED',
        'MONITORING',
        'REROUTED',
        'REROUTE_APPROVAL_REQUIRED',
        'REPLAN_REQUIRED',
        'PAYMENT_REQUIRED',
        'COMPLETED',
        'CANCELLED'
    ));
