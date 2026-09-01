ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS max_dc_charge_power_kw double precision NOT NULL DEFAULT 50,
    ADD COLUMN IF NOT EXISTS charging_efficiency double precision NOT NULL DEFAULT 0.90;

ALTER TABLE autopilot_stops
    ADD COLUMN IF NOT EXISTS effective_power_kw double precision NOT NULL DEFAULT 0;
