# Vidyut Spring Boot API

Java 17 / Spring Boot 3.3.7 backend for authentication, charging infrastructure, Autopilot, payments, marketplace collaboration, and isolated Host, Company, and Admin operations.

## Account and authority model

- `INDIVIDUAL` accounts may hold `ROLE_EV_USER`, `ROLE_HOST`, or both.
- `COMPANY` accounts hold only `ROLE_COMPANY`.
- `ADMIN` accounts hold only `ROLE_ADMIN` and use the separate Admin authentication flow.
- EV, Host, and Company data is partitioned across `ev_user_profiles`, `host_profiles`, and `companies`.

`POST /api/auth/login` returns `allowedModes`, `activeMode`, and a JWT scoped to one authority. A dual EV/Host account switches through `POST /api/auth/switch-mode`, which returns a replacement token. Ownership-sensitive endpoints derive identity from the verified JWT rather than accepting an account ID from the client.

Protected route families include `/api/ev/**`, `/api/host/**`, `/api/company/**`, and `/api/admin/**`. JPA validation and PostgreSQL constraints prevent individual, company, and Admin profile mixing.

## Autopilot API

Primary endpoints are under `/api/ev/autopilot`:

- `POST /intent/parse` — parse the journey text bar into structured fields.
- `POST /vehicles/recommend` — compare owned vehicles against route and charger coverage.
- `POST /trips/preview` — read-only route and charging preview.
- `POST /trips` — confirm or launch according to autonomy mode.
- `GET /trips/current` — recover the current ongoing journey.
- `POST /trips/{id}/start` — start a confirmed trip.
- `POST /trips/{id}/simulate-fault` — exercise charger-failure recovery.
- `POST /trips/{id}/approve-reroute` — approve a pending reroute.
- `POST /trips/{id}/complete-charging` — complete the active charging stop.
- `POST /trips/{id}/experience` — record route outcome memory.

The intent parser is deterministic for supported journey fields; the separate Python agent can add conversational planning and tool orchestration.

## Feasibility

Deadline feasibility is a hard part of the overall result:

```text
overallFeasible = reserveFeasible
               && budgetFeasible
               && deadlineFeasible
```

When `arriveBy` is present, `DeadlineEvaluator` returns the requested arrival, expected arrival, available journey minutes, feasibility, and `minutesLate`. Times use `HH:mm`; overnight deadlines are handled by rolling the requested clock time into the next day when necessary.

## Charging-time model

Vehicle records include efficiency, maximum AC power, maximum DC power, charging efficiency, and supported connectors. The optimizer splits the requested SoC increase across charging-curve segments and uses:

```text
battery-side power = min(charger rating, vehicle DC limit, curve-segment power)
                   × charging efficiency
```

The default profile uses:

- 0–60%: configured vehicle DC maximum
- 60–80%: 80% of the configured maximum
- 80–100%: 40% of the configured maximum

The reported stop includes effective battery-side power and rounded-up charging minutes. This prevents a 150–180 kW station rating from being treated as the power every vehicle can accept.

## OpenStreetMap routing and failure handling

OSRM supplies road geometry, duration, and station matrices. Local routing defaults to `http://localhost:5000`; configure `VIDYUT_OSRM_BASE_URL` when the service is elsewhere.

`VIDYUT_OSRM_REFERENCE_BASE_URL` may point to a wider-coverage second engine. Development defaults it to the public OSRM demo service for comparison, but the public service is not suitable as a production dependency. The shortest valid direct route determines which engine owns the entire final plan.

Failures degrade safely:

- If both road engines fail, route preview uses a labeled conservative estimate of `1.30 ×` great-circle distance at `50 km/h`.
- If a station table is partially unavailable, fallback estimates are limited to the missing charger legs.
- Connector, energy, reserve, budget, and deadline constraints remain enforced for estimated plans.
- The response explains that live navigation must be confirmed before departure.

Place names use `VIDYUT_GEOCODER_BASE_URL`. Development defaults to public Nominatim with Indian country filtering, an in-memory cache, and a one-request-per-second limiter. Configure a self-hosted or contracted geocoder for production.

Important routing variables:

| Variable | Default |
| --- | --- |
| `VIDYUT_OSRM_BASE_URL` | `http://localhost:5000` |
| `VIDYUT_OSRM_REFERENCE_BASE_URL` | blank in base config; public demo in development |
| `VIDYUT_OSRM_PROFILE` | `driving` |
| `VIDYUT_OSRM_SNAP_RADIUS_METERS` | `20000` |
| `VIDYUT_OSRM_MAX_TABLE_LOCATIONS` | `100` |
| `VIDYUT_GEOCODER_BASE_URL` | `https://nominatim.openstreetmap.org` |
| `VIDYUT_GEOCODER_MIN_INTERVAL_MS` | `1000` |
| `VIDYUT_AUTOPILOT_CURRENT_TRIP_MAX_AGE_HOURS` | `72` |

## Host operations

The Host API includes profile/KYC/bank/email verification, properties, availability, bookings, payouts, reviews, notifications, reports, live monitoring, connector maintenance impact, and Host Assistant actions. Host questions use the role-scoped Python agent with Gemini, OpenRouter, and a deterministic Spring answer in that order; the model has no mutation tools.

Live occupancy is derived from charging sessions and synchronized to connector state. Maintenance impact identifies affected users, repair estimate, modeled revenue loss, and compatible alternatives. The Host Assistant can prepare actions but marks external commitments and finance applications as requiring Host approval.

The Prince development seeder creates an idempotent multi-station portfolio across the Lucknow–Kanpur–Jhansi–Bhopal corridor plus an Agra solar site. It includes TATA demo equipment, mixed connectors, live sessions, a fault-ready charger, operator comparisons, and solar purchase/finance/RESCO scenarios.

`POST /api/ev/autopilot/trips/{tripId}/simulate-fault` now runs the complete Taskmaster demo transaction. It faults the matching reserved connector, follows the journey's autonomy mode, releases and replaces the booking when feasible, creates a Company maintenance ticket, notifies the Host and Company, creates an Admin network incident, and records a system-agent audit event. Only Full Autopilot executes the replacement without approval.

## Company and marketplace operations

Company endpoints cover stations, connectors, sessions, maintenance, pricing, bookings, staff, reports, revenue, settlements, products, Host opportunities, saved properties, surveys, proposals, and partnership projects.

The Company Assistant is company-scoped and follows one authority mode: recommend only, ask before actions, or Autopilot within configured limits. Its explanations use Gemini, then OpenRouter, then the deterministic Spring answer, while action execution stays in the approval- and ownership-checked Company endpoint. Expansion Intelligence is a separate read-only ranking view; it does not duplicate assistant authority controls.

Verified companies can inspect the complete allowed Host/property profile before survey decisions, including location, power, parking, evidence, verification stage, previous charger history, and reputation. Contact and action access remains gated by trust and marketplace state.

## Admin control plane

The Admin Portal has separate authentication, role capabilities, and audit records. It supports company/Host/property/product/station verification, property workflow steps, incidents, maintenance, settlements, support, announcements, green schemes, staff access, and AI network memory.

Routine enforcement is scoped to the smallest affected capability or asset. Examples include restricting bookings, freezing payments, pausing listings, disabling a station/charger, or freezing settlements. Emergency full identity restriction is reserved for Super Admin and requires a reason.

## Demo data

Development enables `VIDYUT_DEMO_DATA_ENABLED` by default. Idempotent seeders provide:

- 112 manually curated corridor stations.
- 777 district stations covering 36 states and union territories.
- CCS2, Type 2, CHAdeMO, GB/T, and Type 1 connectors.
- Priyanshu Sharma garage with eight real/demo vehicle profiles.
- Prince Host, campus outlet, charging-session, marketplace, and operations scenarios.

All seeded infrastructure, businesses, prices, performance, finance, and eligibility data is synthetic. See [`src/main/resources/demo/README.md`](src/main/resources/demo/README.md).

## Local PostgreSQL

```powershell
$env:SPRING_DATASOURCE_PASSWORD = "your_postgres_password"
mvn spring-boot:run
```

Common configuration:

- `SPRING_DATASOURCE_URL` — development default database name is `vidyut`.
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET` — mandatory production secret.
- `VIDYUT_AGENT_BASE_URL` — defaults to `http://localhost:8001`.
- `VIDYUT_DEMO_DATA_ENABLED` — controls synthetic seeders.
- `VIDYUT_DEMO_PAYMENTS_ENABLED` — controls demo wallet/payment mutations.
- `VIDYUT_ADMIN_BOOTSTRAP_ENABLED` and `VIDYUT_ADMIN_*` — local Admin bootstrap settings.

Do not use development defaults for production credentials.

## Tests

The test tree currently contains 19 focused source files covering Admin access/controls, agent requests, route optimization, deadlines, NLP intent parsing, vehicle ranking, OSRM fallbacks, location resolution, corridor filtering, station behavior, demo seeders, and the Priyanshu garage.

```powershell
mvn test
```

Use `mvn clean test` when a clean target directory is required.
