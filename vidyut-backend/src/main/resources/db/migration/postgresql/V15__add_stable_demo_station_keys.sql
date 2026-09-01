ALTER TABLE charging_stations
    ADD COLUMN IF NOT EXISTS demo_seed_key VARCHAR(120);

CREATE UNIQUE INDEX IF NOT EXISTS uk_charging_stations_demo_seed_key
    ON charging_stations (demo_seed_key)
    WHERE demo_seed_key IS NOT NULL;
