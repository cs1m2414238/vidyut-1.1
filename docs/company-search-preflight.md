# Company network rollout preflight — 2026-08-31

Production promotion and Firebase publication were explicitly approved and completed on 2026-08-31. Chrome UI/network verification remains pending because the browser extension and native host are unavailable; API, agent and published-build checks pass.

## Current revisions

- Backend production: `vidyut-backend-00041-bib`, READY, 100% traffic; tag `operator-review` also points to it.
- Previous backend production / rollback reference: `vidyut-backend-00036-xpf`.
- Previous code-verification revision: `vidyut-backend-00040-yib`; do not promote it because its seeding environment differs from production.
- Agent production: `vidyut-agent-00015-jem`, READY, 100% traffic; tag `operator-review` also points to it.
- Previous agent production / rollback reference: `vidyut-agent-00014-rb6`.
- Firebase Hosting version: `1cd6efbec1e745fb`. Current production JavaScript: `/assets/index-66SsSx2E.js`; previous build was `/assets/index-vQhxAr0V.js`.

The sections below retain the staging history and approval boundary. The user subsequently explicitly approved promoting `00041-bib`, promoting the agent if needed, and deploying Firebase. The final section records that completed rollout.

## Startup diagnosis

Failed revision `vidyut-backend-00037-85h` initialized Hibernate's EntityManagerFactory, then failed while constructing `UserServiceImpl`: no `PasswordEncoder` bean was available. System logs report `Container called exit(1)` followed by a cancelled TCP startup probe. The inspected logs contain no OOM/SIGKILL or schema-validation error. Shutdown thread warnings follow the bean failure.

Application configuration classes were omitted by an overly broad upload exclusion. The backend upload now excludes only root `/config/` local secrets and includes Java configuration packages, including `PasswordEncoderConfig`. No migration, Cloud SQL, IAM, memory, port or service-name change was required.

Staged revision 00039 logs:

- 03:59:06 UTC: 26 migrations validated; schema version 26; no migration necessary.
- 03:59:15 UTC: Tomcat started on port 8080.
- 03:59:16 UTC: VidyutApplication started.
- 03:59:52 UTC: existing demo initializer completed.

The staging tag shares the production database. Starting a new backend revision runs the currently enabled restorative demo initializer; zero traffic is not a separate database environment.

## Verification before the city/type correction (revision 00039)

| Check | Result |
| --- | --- |
| Staged authenticated Company login | PASS; confirms the password-encoder startup failure is resolved |
| `Agra`, `agra`, `agar`, `Agra charger`, `agr charger` | PASS: 2 authorized stations and all 5 chargers |
| `DEMO-AGRA-CCS2-01` | PASS: exactly one connector, 180 kW, ONLINE |
| Operator fault preparation, `approved=false` | PASS: AWAITING_APPROVAL; connector remains ONLINE |
| Host access to Company network search | PASS: HTTP 403 |
| Exact connector disruption and healthy second connector | Unit tests PASS; the later revision 00040 section records the completed live rehearsal |
| Existing env/secrets, resources, port, service account, Cloud SQL attachment | Unchanged between backend revisions 00036 and 00039 |
| Production frontend and staged agent backend URL | Both target `vidyut-backend-558967442483.asia-south1.run.app` |
| Agent health | READY; 38 Python tests pass |
| Frontend build and lint | PASS |

## Corrected staging and live rehearsal

Revision `vidyut-backend-00040-yib` contains the city/type correction. It was deployed with no traffic and with `DEMO_SEED_ENABLED=false`, while `VIDYUT_DEMO_DATA_ENABLED=true` preserves demo visibility. This prevents startup restoration from resetting the shared production demo state. The seeder source and its keyed upsert behavior remain unchanged; its existing-record and disabled-mode tests pass.

Direct staged checks:

- `Agra`, `agra`, `agar`, `Agra charger`, and `agr charger`: 2 authorized Agra stations and all 5 chargers.
- `agra ccs2` and reordered `CCS2, Agra`: all 4 CCS2 connectors across both Agra stations.
- `DEMO-AGRA-CCS2-01`: one connector, ID 6, 180 kW, ONLINE.
- Unknown location: zero records; Host role: HTTP 403.
- Clear search: all 889 stations and 2,751 connectors.
- The deployment did not add, remove or alter recorded connector operating state, stations, Host properties, offers or installation records.

Live connector rehearsal used the existing reserved Delhi → Bhopal trip 9, whose Agra stop persisted connector ID 6 and code `DEMO-AGRA-CCS2-01`. After journey monitoring began, the unapproved Company action returned `AWAITING_APPROVAL` and made no change. The approved action then faulted only connector 6. Connector 7 remained ONLINE and available. The incident reported one affected journey, one driver approval, zero automatic reroutes, and `backupConnectorAvailable=true`; trip 9 entered `REROUTE_APPROVAL_REQUIRED`. Replacement stops stored exact non-faulted connector IDs and had no booking IDs until approval. Repeated reads did not approve the reroute.

Cleanup restored connector 6 to ONLINE, available, health 98, with fault code/reason cleared. The journey was ended as CANCELLED to release reservations 37–40; its audit history remains. Vehicle battery and wallet balance remained unchanged. Station, connector and property inventories still match the baseline. The pre-existing Company maintenance ticket 2 for connector 6 (created 2026-08-29) remains OPEN and was deliberately not altered.

Backend production remains 100% on `vidyut-backend-00036-xpf`. No agent or Firebase deployment occurred during this approved staging step. Promotion remains blocked until separate explicit approval.

## Final candidate with production configuration

Revision `vidyut-backend-00041-bib` was created on 2026-08-31 using the exact immutable image from `00040-yib`, without rebuilding source:

```text
asia-south1-docker.pkg.dev/vidyut-autopilot/cloud-run-source-deploy/vidyut-backend@sha256:d6431675c4ffec76c82ace41c6afd097a560f8375cfdc8d3345d7612d53b4766
```

The complete environment matches `00036-xpf`: `DEMO_SEED_ENABLED=true` was restored, and the staging-only `VIDYUT_DEMO_DATA_ENABLED` entry was removed. Demo visibility follows the existing fallback to `DEMO_SEED_ENABLED`. Environment fingerprints match, including all existing values and secret references. Runtime settings, service account, resource limits, ports, startup probe and runtime annotations match production. The backend IAM policy fingerprint, service identity, production URL, agent environment/traffic and Firebase asset remain unchanged. No Cloud SQL or IAM mutation was performed.

| Final candidate check | Result |
| --- | --- |
| Revision READY / startup probe | PASS; TCP probe succeeded; Tomcat listening on 8080 |
| Flyway V26 | PASS at 04:38:22 UTC: 26 migrations validated, schema version 26, no migration necessary; V26 source checksum unchanged |
| Login | PASS for Company, EV Owner and Host demo accounts |
| `Agra` / `agar` | PASS: 2 authorized stations and all 5 chargers; no unrelated cities |
| `agra ccs2` / `CCS2, Agra` | PASS: all 4 CCS2 connectors |
| `DEMO-AGRA-CCS2-01` | PASS: exactly connector 6, 180 kW, ONLINE |
| Clear / unknown search | PASS: 889 stations and 2,751 chargers / zero results |
| Company authorization | PASS: Host token denied Company network access with HTTP 403 |
| Unapproved Company fault action | PASS: AWAITING_APPROVAL; connector remains ONLINE |
| Canonical inventory and duplicates | PASS: identical IDs/counts for 889 stations, 2,751 chargers, 4 properties, 6 vehicles, 4 existing maintenance tickets and zero persisted installation proposals; no new duplicate codes/names/titles |
| Startup reset/restoration | PASS: enabled seeder completed at 04:39:02 UTC; existing Agra connector is ONLINE, available, health 98, fault metadata clear, source DEMO_SEED_RESET |
| Prior rehearsal cleanup | PASS: trip 9 remains CANCELLED, reservations 37–40 remain CANCELLED; battery levels and wallet balance unchanged |
| Production traffic | PASS: 100% on `vidyut-backend-00036-xpf`; candidate receives 0% |

The enabled seeder refreshed existing heartbeat, status-change and vehicle telemetry timestamps and reset the Agra connector's status source to `DEMO_SEED_RESET`. Connector identity, operating status, availability, maintenance mode, health and power values remain unchanged. Full station and vehicle comparisons match after accounting for those expected seed metadata updates and unordered connector lists. Host property and existing maintenance-ticket records are unchanged. No destructive reset or cleanup was invoked.

The connector fault/reroute/restore live rehearsal documented above ran on the same immutable image in revision 00040. This final candidate check repeated login, search, approval preparation, configuration parity and startup restoration; it did not start another journey or simulate another fault.

Startup logs contain the existing Flyway warning that PostgreSQL 18.4 is newer than its tested support range. Validation nevertheless succeeds; there is no startup ERROR or container termination. No dependency or database change was made for this warning.

At the end of the final-candidate step, promotion was stopped pending separate explicit approval. Neither the agent nor Firebase was deployed in that step. Do not promote `00040-yib`, whose staging configuration differs from production.

## Explicitly approved production rollout

The subsequent user instruction explicitly approved: promote backend `00041-bib`, verify production search, promote the agent if needed, build the frontend and deploy Firebase Hosting. That sequence completed successfully. No backend search changes or new backend/agent builds were made during promotion.

| Production check | Result |
| --- | --- |
| Backend traffic | PASS: `vidyut-backend-00041-bib` READY, 100% |
| Company production login and search | PASS: `Agra`, `agra`, `agar`, `Agra charger`, `agr charger` return 2 stations / 5 chargers |
| Combined city/type | PASS: `agra ccs2` and `CCS2, Agra` return all 4 CCS2 connectors |
| Exact / clear / missing query | PASS: exact connector 6; clear returns 889 stations / 2,751 chargers; unknown location returns zero |
| Authorization and approval | PASS: Host token rejected with 403; unapproved fault preparation returns AWAITING_APPROVAL and leaves connector ONLINE |
| Agent promotion | PASS: `vidyut-agent-00015-jem` READY, 100%; environment matches `00014-rb6`, with the same backend URL |
| Agent integration | PASS: staged agent called `get_company_operations_context`; production backend `/api/company/ai/ask` returned the correct Agra context using GEMINI / `gemini-3.6-flash`, without fallback |
| Frontend build / lint | PASS; existing bundle-size warning only |
| Firebase deployment | PASS: `projects/558967442483/sites/vidyut-autopilot/versions/1cd6efbec1e745fb` |
| Published build integrity | PASS: production HTML, JavaScript and CSS are byte-identical to the fresh local build |
| API URL / search wiring | PASS by source and published-bundle inspection: Company search calls `/api/company/{stations\|chargers}?q=...`, debounces 300 ms, and renders the returned array without client pagination |
| Browser CORS | PASS: OPTIONS preflight and authenticated GET permit `https://vidyut-autopilot.web.app` |
| Chrome UI / Network panel / hard refresh | PENDING: Chrome is running, but its ChatGPT browser extension and native host are absent. No browser request trace or rendered-row screenshot is claimed. Permission to use the connected in-app browser was requested. |

The production API request `GET /api/company/chargers?q=Agra` returned HTTP 200 with exactly:

- `DIST-SOI-09-146-CCS2-01` — Vidyut Agra District Demo Hub, 120 kW.
- `DIST-SOI-09-146-CCS2-02` — Vidyut Agra District Demo Hub, 60 kW.
- `DIST-SOI-09-146-TYPE2-01` — Vidyut Agra District Demo Hub, 22 kW.
- `DEMO-AGRA-CCS2-01` — Agra Demo Charging Hub, 180 kW.
- `DEMO-AGRA-CCS2-02` — Agra Demo Charging Hub, 120 kW.

All five were ONLINE. This is direct API evidence, not a Chrome Network capture. The actual query parameter is `q`.

Published JavaScript SHA-256: `28b7681b4ea74d513772dbca8ed3a56ce11fbc67e9e56457ee28af5cb2d4e6ac`. Published CSS is `/assets/index-BypUXLxT.css`. Firebase currently serves HTML with `Cache-Control: max-age=3600`, so an existing Chrome tab can need Ctrl+Shift+R to fetch the new build. The production URL remains `https://vidyut-autopilot.web.app`.
