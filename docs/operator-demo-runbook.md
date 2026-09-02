# Cross-agent operator demo runbook

This runbook is the canonical rehearsal for the fault-to-recovery workflow. It is intentionally narrow: one connector incident, one affected journey, one safe recovery, and role-correct visibility.

## Success contract

The rehearsal is complete only when all of these statements are true:

1. Company marks exactly `DEMO-AGRA-CCS2-01` faulty.
2. The backend commits one fault event and creates or reuses one maintenance incident.
3. The affected EV journey enters recovery; the healthy sibling connector stays online.
4. Unsafe or unavailable alternatives are rejected by backend validation.
5. A safe replacement route is prepared without creating a booking.
6. The EV Owner explicitly approves the fresh plan.
7. Approval creates the replacement booking and updates the route once.
8. Repeating approval returns the completed result without another booking or route update.
9. The property-owning Host sees its hosted-charger incident; an unrelated Host does not.
10. Company, Host, and EV activity share the connector correlation trace without sharing private queue contents.

## Preflight

Use the synthetic demo accounts and data documented in the project. Before recording:

```powershell
cd vidyut-backend
mvn test

cd ..\vidyut-ai\agent
python -m unittest discover -s tests -v

cd ..\..\vidyut-web
npm run lint
npm run build
```

Confirm the designated connector is `ONLINE`, available, and not in maintenance mode. Confirm `DEMO-AGRA-CCS2-02` is also online so the demo can prove connector-level isolation. Create or select a journey whose persisted Agra stop identifies `DEMO-AGRA-CCS2-01` exactly.

For the successful recovery rehearsal, publish a fresh synthetic vehicle position with 95% SoC immediately before evaluation, then approve within the two-minute GPS freshness window. A lower or stale snapshot is expected to fail closed; that is useful safety evidence, but it is not the clean success take.

## Rehearsal

### 1. Establish the EV objective

Open the EV Owner Agent and show the active journey. The work queue should identify journey monitoring as the current objective. Do not rely on station name alone; show the exact connector code in the journey.

### 2. Create the Company fault

In Company, find `DEMO-AGRA-CCS2-01`, prepare the change from `ONLINE` to `FAULT`, review the synthetic reason, and approve. Show that the work queue explains what happened, why it matters, what Vidyut did, and any remaining approval.

Verify the sibling `DEMO-AGRA-CCS2-02` remains online. The fault event and every resulting work item should carry the same connector correlation ID.

### 3. Show cross-agent response

Return to EV Owner. The journey should enter recovery and list evaluated alternatives. No replacement booking should exist before approval. Unsafe, occupied, incompatible, faulted, or route-invalid alternatives must not appear as selectable recovery actions.

Open the owning Host workspace. Show only the incident relevant to that Host's property, with no Company-only controls. If an unrelated Host account is available, confirm its queue is empty for this incident.

### 4. Approve recovery once

In EV Owner, review the prepared replacement and approve it. Show the completed state, booking identifier, and route update in the activity timeline. Submit the same approval again only through the controlled test or API rehearsal: it must return the recorded completion and must not create another booking or second route mutation.

### 5. Close the loop

In Company, create or reuse the maintenance ticket and show its completed work item. Restore the connector through a separately reviewed action. Restoration closes active connector monitoring but does not undo the EV Owner's accepted route.

## Suggested 3.5-minute recording

```text
0:00  The failed-charger problem
0:20  Three role-specific agents
0:45  EV journey and exact connector
1:10  Company creates the fault
1:30  Scoped cross-agent response
2:00  Safe recovery and EV Owner approval
2:40  Host visibility boundary
3:00  Work queue, correlation trace, and architecture
3:30  Exact-once outcome and close
```

## Screenshot set

Use this five-image core set:

1. `docs/screenshots/ev-agent-recovery-approval.png` — failed connector, prepared recovery work, approval boundary, and correlation trace.
2. `docs/screenshots/ev-agent-recovery-completed.png` — completed work item and verified connector-reservation activity.
3. `docs/screenshots/ev-agent-recovery-route-updated.png` — applied recovery route and final safety evidence.
4. `docs/screenshots/company-agent-completed-incident.png` — Company incident completion and monitoring.
5. `docs/screenshots/host-agent-property-incident.png` — property-scoped Host incident visibility.

`docs/screenshots/ev-agent-recovery-approval-detail.png` and `docs/screenshots/company-agent-approval-queue.png` are optional detail images when the EV or Company preparation step needs its own slide.

Use a consistent viewport and hide browser chrome, secrets, tokens, personal notifications, and unrelated demo clutter.

## Freeze evidence

Record the command output or CI link for each item; do not infer a pass from an earlier run.

```text
[ ] Backend tests pass
[ ] Agent tests pass
[ ] Web build and lint pass
[ ] Fault-to-recovery rehearsal passes repeatedly
[ ] EV / Host / Company role isolation passes
[ ] Repeated execution produces no duplicate action
[ ] Work queue and current objective are readable
[ ] Four screenshots are captured
[ ] Two-to-five-minute recording is complete
[ ] Tracked files contain no production secrets
```

The frozen revision should remain immutable. Continue later work from a development branch rather than changing the frozen revision.
