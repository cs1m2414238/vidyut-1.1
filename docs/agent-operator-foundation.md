# Agent operator foundation

Vidyut has one shared, persistent operator layer for the EV Owner, Host, and Company workspaces. The three agents reason about different responsibilities while the backend owns identity, scope, approvals, state validation, idempotency, and mutations.

## Durable records

- `agent_outbox_events` stores domain events in the same transaction as the originating change. `event_key` is unique, so a repeated publisher cannot create a second logical event.
- `agent_domain_events` is the durable, role-safe projection ledger. It keeps the event type, aggregate, correlation ID, source outbox record, bounded evidence, and projection time.
- `agent_work_items` is the account-and-workspace-scoped queue. It stores the objective, reasoning chain, typed action payload, expected state, idempotency key, correlation ID, approval lease, result, failure reason, retry count, and lifecycle timestamps.
- `agent_activities` is the correlated activity timeline used to show what happened across agents without exposing another role's private queue.

The outbox dispatcher reads eligible records and hands each one to an isolated transaction. That transaction locks and rechecks the record, projects it, and marks it published atomically. Competing dispatchers can observe the same candidate, but only one can process it. Failed attempts record a bounded retry with backoff and an explicit failure reason.

## Work queue states

The UI groups backend lifecycle states into five operator-friendly lanes:

```text
In progress
Needs approval
Monitoring
Completed
Failed / blocked
```

The highest-priority active item is also shown as the current objective. Every item can explain:

```text
What happened
Why it matters
What Vidyut already did
What Vidyut proposes
Why approval is required
Expected impact
```

`GET /api/agent/work-queue` and `GET /api/agent/work-queue/activity` derive account and workspace from the authenticated JWT. Callers cannot select another account or role. Admin is deliberately excluded because this operator queue is for EV Owner, Host, and Company.

## Approval and exact-once execution

Prepared actions receive an opaque work-item ID, idempotency key, correlation ID, expected-state snapshot, and a 15-minute approval lease. Approval never means "run the old plan regardless": the domain service locks the affected record and revalidates ownership, policy, connector state, route state, and price snapshot before mutation.

- A changed snapshot returns `APPROVAL_STALE` and performs no mutation.
- A repeated approval of a completed item returns the recorded result and performs no second mutation.
- An expired lease becomes stale and must be planned again.
- A blocked or failed action keeps its failure reason in the queue.
- Refreshing a plan rotates its idempotency key, so approval tokens from an older plan cannot authorize a newer one.

EV recovery retains its existing trip and connector locks. A second approval cannot create another booking or route update. Company maintenance creation reuses an active ticket for the connector. Host actions use the same prepared-action and current-state checks.

## Cross-agent projection boundaries

A connector fault uses one correlation ID, such as `connector-21`, and produces scoped projections:

- Company receives operational triage for equipment it operates.
- Only the Host that owns the affected property receives hosted-equipment visibility.
- An affected EV journey receives a recovery item tied to its recovery incident and route plan.

An unrelated Host receives neither the incident work item nor its evidence. Restoration records a separate event, closes active connector work items, and does not silently undo an EV Owner's accepted route.

## Bounded autonomy

Company `AUTOPILOT` can bypass a second approval only for the two saved per-tool permissions:

- isolate a faulty connector from new assignments;
- create or reuse its maintenance ticket.

Pricing, station-manager notifications, synthetic demo controls, restoration, contracts, payouts, and destructive actions remain approval-gated. `RECOMMEND_ONLY` never mutates. `ASK_BEFORE_ACTIONS` always waits. Host mutations remain approval-gated.

## Verification snapshot

The operator foundation is covered by migration, publisher, outbox processor, dispatcher, projection, queue lifecycle, Company action, EV recovery, and cross-agent integration tests. The cross-agent integration scenario verifies one fault event, Company and owning-Host visibility, unrelated-Host isolation, a shared correlation trace, one maintenance result on repeat approval, and a healthy sibling connector remaining online.

Current local verification:

```text
Backend: 141 tests, 0 failures, 0 errors, 0 skipped
Agent service: 44 tests, all pass
Web lint: pass
Web production build: pass
```

## Deliberate limits

- The transactional outbox is database-backed; no external broker is required for this bounded workflow.
- Host and Company operational memory is structured around work items and activity, not free-form conversation transcripts.
- The dispatcher handles committed events; it is not a general-purpose workflow engine.
- Payments and destructive infrastructure changes are outside the autonomous action boundary.

The governing rule is unchanged: an agent may reason and rank, but Spring services validate ownership, policy, idempotency, current state, and every mutation.
