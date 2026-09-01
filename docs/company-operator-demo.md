# Company operator demo

Company operates hardware, including Company-operated Host-partnered stations. The Host can inspect its hosted equipment and request maintenance. Drivers can report an issue and approve journey recovery; reports do not alter shared connector status or battery telemetry.

## Recording flow

1. Create an EV Owner journey and check its displayed **exact charger code**. New plans persist connector IDs. The migration only backfills legacy plans when type and rated power identify a unique connector.
2. Open **Company → Chargers** and use the **top search bar**. Search `Agra`, `agar`, `agra ccs2`, an address such as `Fatehabad`, or `DEMO-AGRA-CCS2-01`. Search stays on the current page; Stations shows station rows, Chargers shows every matching connector record.
3. Open the selected connector. Change **Operational status** from `ONLINE` to `FAULT`. Keep the synthetic event clearly labelled. Review the reason, click **Save changes**, then **Approve**. Cancel performs no write.
4. The backend records `DEMO_CHARGER_FAULT`, the reason, source `COMPANY_DEMO_CONTROL`, and the transition time. It prepares recovery for journeys assigned to that connector. A healthy second connector does not suppress the event and stays usable.
5. Return to the driver journey. It refreshes on focus and every five seconds while visible, except during a driver action. Review and approve the backend-calculated replacement. The alternative may be another usable connector at the same station or another station; Mathura is not hard-coded or guaranteed.
6. Ask the Host Agent **“Are any chargers on my properties having issues?”** It reads the shared operational state and can prepare a service request.
7. Restore the same connector to `ONLINE` through the Company editor and approve. Alternatively ask **“Restore DEMO-AGRA-CCS2-01”**, review the action and approve it. Restoration clears synthetic fault telemetry and closes the associated synthetic incident/work order. It does not undo a driver's accepted route.

## Company Agent

- Network summaries and maintenance priorities use stored status, health, reservations, exact-connector journey assignments, transition times, queue and occupancy snapshots.
- Company-owned and Host-partnered assets remain distinct. Explicit operator ownership takes precedence over legacy supplier attribution.
- Expansion ranking uses discoverable verified properties, bays, available load and straight-line distance to operating stations. A requested `120 kW CCS2` setup filters out sites that cannot meet that requirement. A survey must establish spare capacity.
- Connector compatibility, AC-only sites, Company proposal comparisons and recorded gross charging amounts are read-only analyses.
- Every agent write requires approval, including saved `AUTOPILOT` mode. Canonical demo actions check current status under a database lock and reject stale approvals or active charging sessions.
- Creating a maintenance ticket does not silently change hardware, assign a fictional technician or invent an arrival time. Assignment and resolution use the Maintenance workspace.

## Deliberate limits

The assistant does not invent repair time, lost revenue, fees, Host payouts, net income, utilization uplift or payback. Missing values are unknown. No independent historical peak-demand model is added.

Only the current Company's persisted installation proposals are exposed. Synthetic comparison examples in the Host assistant are not persisted commercial offers. Offer mutations remain in the existing property/installation workflow; this change does not create an accept/withdraw API for those examples, and Company cannot accept on a Host's behalf.

Agent evidence detail lists are capped at 25; aggregate counts cover the selected network. The Stations and Chargers search endpoints are not capped or filtered from a client page: `GET /api/company/stations?q=...` and `GET /api/company/chargers?q=...` match the complete authorized network before response mapping. State names are searchable where stored in a station address. Search normalizes case, whitespace and punctuation, accepts reordered tokens and adjacent transpositions, and prioritizes an identified city over incidental highway-address mentions. No Agra-specific alias is used. The top bar debounces requests for 300 ms and discards stale responses.

## Validation and rollout

Run `mvn test` in `vidyut-backend`, `npm run build` and `npm run lint` in `vidyut-web`, and `.venv/Scripts/python -m unittest discover -s tests` in `vidyut-ai/agent`. Production search verification is read-only: opening the connector editor does not change its status.

Backend migration **V26** is already applied and validated in production; do not modify or reapply it manually. Cloud services are `vidyut-backend` and `vidyut-agent` in project `vidyut-autopilot`, region `asia-south1`; Firebase serves `https://vidyut-autopilot.web.app`. Preserve existing environment/secrets, stage new revisions with no traffic and verify readiness. Production promotion and Hosting publication require separate explicit approval. Backend upload excludes root `/config/` local secrets, while keeping `src/main/java/**/config/` application classes.

The explicitly approved production rollout now serves `vidyut-backend-00041-bib` and `vidyut-agent-00015-jem` at 100% traffic. The backend uses the verified image from `00040-yib` with the original `00036-xpf` production environment, including enabled demo seeding. Firebase Hosting version `1cd6efbec1e745fb` publishes the new search UI. Production API search, agent integration, CORS and published-build integrity checks pass. The same backend image previously passed the connector-level Delhi → Bhopal fault/reroute/restore rehearsal. See [company-search-preflight.md](company-search-preflight.md) for evidence. Chrome UI/network verification is still pending because the required browser connection is unavailable; hard-refresh existing tabs before recording.
