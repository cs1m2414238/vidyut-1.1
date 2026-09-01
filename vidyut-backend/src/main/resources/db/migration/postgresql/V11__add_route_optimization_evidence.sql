ALTER TABLE autopilot_trips
    ADD COLUMN IF NOT EXISTS base_route_distance_km double precision NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS charging_detour_distance_km double precision NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS base_drive_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS charging_detour_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_charging_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_queue_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS connection_overhead_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS feasible_alternatives_compared integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS optimization_summary varchar(1000),
    ADD COLUMN IF NOT EXISTS route_engine varchar(40);

ALTER TABLE autopilot_stops
    ADD COLUMN IF NOT EXISTS route_offset_km double precision NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS connection_minutes integer NOT NULL DEFAULT 0;
