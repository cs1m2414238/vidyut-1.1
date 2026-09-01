# Agent-driven charger incident recovery

Implementation status: local code and regression tests; not deployed. The production rollout previously completed for Company search is separate from this recovery change.

## Responsibility and execution boundary

1. Company connector operations record an incident against the exact planned connector. A healthy sibling connector does not hide it. Driver reports record an incident without changing operator hardware status.
2. The active EV journey view receives the incident through its existing authenticated polling. It calls `/api/ev/autopilot/trips/{id}/recovery/run`; the backend checks journey ownership, then forwards the driver's credential to the existing Python agent service at `/v1/recovery`. No database transaction is held across that callback.
3. The EV Agent calls `get_recovery_context` and `get_safe_recovery_candidates`. The backend owns vehicle energy calculations, actual road distances, connector eligibility, budget, deadline and whole-journey validation.
4. The agent selects an opaque backend-issued plan ID. Direct continuation to a reachable next planned charger is preferred; otherwise it selects from safe bridge plans. Gemini can choose among those options. If model selection fails or returns an unoffered ID, the agent uses an explicitly labeled policy fallback. It never invents coordinates or battery numbers.
5. The agent calls `prepare_safe_reroute`. The proposal remains separate from active stops, reservations and navigation.
6. `ASK_BEFORE_ACTIONS` waits for the driver to approve that incident ID and plan ID. Cancel closes the confirmation without sending a mutation. `RECOMMEND_ONLY` stores a suggestion and cannot execute through either approval or automatic endpoints. `FULL_AUTOPILOT` allows the agent's `execute_reroute` tool inside the stored constraints.
7. Execution locks the trip and selected connectors, rechecks current telemetry and every actual road leg, and rejects a materially changed quote. Only then does one transaction release old bookings, reserve all recovery stops, and replace navigation. Reservation failure rolls back cancellation. Repeated execution of the same proposal is idempotent.

Chat's existing `handle_charger_unavailable` tool uses the same Python recovery orchestrator. The legacy booking reroute tool is not used for this workflow.

## Feasibility and evidence

- Position and SoC are captured together. Missing or stale GPS blocks recovery instead of guessing a point near the failed station. A device GPS update requires the driver's current vehicle battery input and must only be used while that device is in the vehicle.
- Canonical demo vehicles use explicitly labeled, persisted progress along the stored road polyline, with matching energy consumption. They do not masquerade as live GPS. Older demo journeys without this evidence need a new plan.
- Straight-line distance is only a discovery bound. Estimated routes and estimated matrix cells cannot establish recovery feasibility. Null matrix cells remain unreachable. Final road legs are checked again after optimization.
- Every proposed road leg must preserve the configured reserve using unrounded battery values. The first bridge target is the minimum whole-percent departure needed for its next selected leg plus reserve and a 3 percentage-point margin, capped by the charging limit. It is not a fixed 80% charge.
- Only complete onward routes become candidates. The failed exact connector is excluded; an eligible healthy sibling can itself be a recovery candidate.
- Budget is the stored cap less completed charging cost. No automatic budget floor or relaxation is applied. New journeys persist an absolute deadline so an expired deadline cannot silently roll over to tomorrow. A legacy journey with only an ambiguous clock deadline must be refreshed.
- Comparisons use the original *remaining* road route from the same current position. Distance, time and cost changes remain signed. An unavailable baseline produces an unavailable comparison, not fabricated zeroes.
- The UI shows backend evidence, all proposed downstream stops, source of position, route engine, actual agent selection provider and execution state. Active map geometry changes only after execution.
- Bookings now optionally persist an exact connector ID and reject overlapping reservations on that connector even when another connector is healthy. Legacy station-only bookings remain supported.

`NO_SAFE_RECOVERY_ROUTE` means no complete route was verified inside all constraints. Its reason distinguishes missing telemetry/road evidence from an exhausted feasible search. Existing reservations remain intact. Do not continue toward an unavailable charger; safely stop and resolve telemetry, route constraints or assistance needs.

## Local validation

Commands:

```powershell
cd vidyut-backend
mvn test
cd ../vidyut-ai/agent
.\.venv\Scripts\python.exe -m unittest discover -s tests
cd ../../vidyut-web
npm run build
npm run lint
```

Recovery tests cover the BMW fixture (66.5 kWh, 170 Wh/km, SoC 39%, reserve 15%): the modeled safe range is about 93.88 km, so a 160 km jump is rejected. A 22 km bridge followed by a 120 km onward leg requires a 49% departure target. These are deterministic test-fixture distances, not claims about the live Delhi–Bhopal road network.

Additional tests cover every-leg reserve, direct next-stop preference, actual roads invalidating matrix candidates, unavailable chargers, changed telemetry, stale/missing GPS, exact connector exclusion, budget/deadline constraints, all autonomy modes, old approval rejection, idempotence, cross-account access, exact connector booking conflicts, transactional rollback and additive migration behavior.

## Deployment and rehearsal still required

- The additive `V27__capture_autopilot_recovery_state.sql` introduces position/navigation/proposal fields, an absolute deadline, and nullable booking connector IDs. It does not backfill guessed telemetry, reset demo inventory, or alter V26. V26 SHA-256 remains `27F71252C035A4B4DCAC89830859956A1600EF6F3B09E22F2178A74D9516B961`.
- Migration tests use local H2 PostgreSQL compatibility mode. They do not prove a production PostgreSQL/Flyway startup. A production-database-sharing staged revision requires the rollout approval process; this change has not been staged or promoted.
- Keep existing service names, secrets, IAM, Cloud SQL settings and production URLs. Backend, agent and frontend must be rolled out as a coordinated feature; deploying only the backend does not install the agent orchestration/UI.
- Rehearse against a staged deployment: start a fresh demo trip; confirm position source and exact connector; fault that connector through approved Company controls; check a healthy sibling does not suppress the incident; observe agent preparation; cancel once and verify no reservation changes; approve and verify complete itinerary, exact bookings and navigation; restore the connector and end the test journey without resetting canonical inventory.
- Test Ask, Full Autopilot and Recommend Only separately. Test expired GPS, changed SoC before approval, unavailable road routing, no reachable charger and a competing reservation.
- Browser interaction, live map rendering, Gemini selection against deployed infrastructure, production PostgreSQL validation and the cross-role live rehearsal are not asserted by local tests.
- Incident delivery currently runs while the EV journey view is active/visible, or through an EV Agent chat request. This is not an always-on background fleet worker. GPS is a fresh snapshot, not continuous vehicle telemetry; movement or stale evidence can require re-evaluation before execution.
