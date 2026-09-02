ALTER TABLE agent_work_items ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE agent_work_items ADD COLUMN correlation_id VARCHAR(100);
ALTER TABLE agent_work_items ADD COLUMN objective_id VARCHAR(100);
ALTER TABLE agent_work_items ADD COLUMN action_bundle_id VARCHAR(100);
ALTER TABLE agent_work_items ADD COLUMN request_id VARCHAR(100);
ALTER TABLE agent_work_items ADD COLUMN what_happened VARCHAR(1000);
ALTER TABLE agent_work_items ADD COLUMN why_it_matters VARCHAR(1000);
ALTER TABLE agent_work_items ADD COLUMN already_done VARCHAR(1000);
ALTER TABLE agent_work_items ADD COLUMN proposed_action VARCHAR(1000);
ALTER TABLE agent_work_items ADD COLUMN approval_reason VARCHAR(1000);
ALTER TABLE agent_work_items ADD COLUMN expected_impact VARCHAR(1000);
ALTER TABLE agent_work_items ADD COLUMN expected_state_json VARCHAR(5000);
ALTER TABLE agent_work_items ADD COLUMN execution_result_json VARCHAR(5000);
ALTER TABLE agent_work_items ADD COLUMN failure_reason VARCHAR(1500);
ALTER TABLE agent_work_items ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE agent_work_items ADD COLUMN max_retries INTEGER NOT NULL DEFAULT 3;
ALTER TABLE agent_work_items ADD COLUMN prepared_at TIMESTAMP;
ALTER TABLE agent_work_items ADD COLUMN approved_at TIMESTAMP;
ALTER TABLE agent_work_items ADD COLUMN execution_started_at TIMESTAMP;
ALTER TABLE agent_work_items ADD COLUMN executed_at TIMESTAMP;
ALTER TABLE agent_work_items ADD COLUMN expires_at TIMESTAMP;

UPDATE agent_work_items
SET idempotency_key = 'work-item-' || id,
    correlation_id = 'legacy-work-item-' || id,
    what_happened = title,
    why_it_matters = detail,
    already_done = 'The work item was captured in the durable operator queue.',
    proposed_action = COALESCE(action_type, 'Continue monitoring this objective.'),
    approval_reason = CASE WHEN status = 'NEEDS_APPROVAL' THEN 'A person must approve this consequential action.' ELSE NULL END,
    expected_impact = detail,
    prepared_at = created_at,
    expires_at = CASE WHEN status = 'NEEDS_APPROVAL' THEN created_at + INTERVAL '15' MINUTE ELSE NULL END;

ALTER TABLE agent_work_items ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE agent_work_items ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE agent_work_items ADD CONSTRAINT uk_agent_work_item_idempotency
    UNIQUE (account_id, workspace, idempotency_key);

CREATE INDEX idx_agent_work_queue_correlation
    ON agent_work_items(correlation_id, updated_at DESC);
CREATE INDEX idx_agent_work_queue_expiry
    ON agent_work_items(status, expires_at);

CREATE TABLE agent_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(180) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    actor_account_id BIGINT REFERENCES accounts(id),
    correlation_id VARCHAR(100) NOT NULL,
    payload_json VARCHAR(5000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    last_error VARCHAR(1500),
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_agent_outbox_dispatch
    ON agent_outbox_events(status, available_at, id);

ALTER TABLE agent_domain_events ADD COLUMN correlation_id VARCHAR(100);
ALTER TABLE agent_domain_events ADD COLUMN outbox_event_id BIGINT;
UPDATE agent_domain_events SET correlation_id = 'legacy-domain-event-' || id;
ALTER TABLE agent_domain_events ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE agent_domain_events ADD CONSTRAINT fk_agent_domain_event_outbox
    FOREIGN KEY (outbox_event_id) REFERENCES agent_outbox_events(id);
ALTER TABLE agent_domain_events ADD CONSTRAINT uk_agent_domain_event_outbox UNIQUE (outbox_event_id);

CREATE TABLE agent_activities (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    workspace VARCHAR(20) NOT NULL,
    work_item_id BIGINT REFERENCES agent_work_items(id) ON DELETE SET NULL,
    correlation_id VARCHAR(100) NOT NULL,
    activity_type VARCHAR(60) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    detail VARCHAR(1500),
    metadata_json VARCHAR(5000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_activity_scope_time
    ON agent_activities(account_id, workspace, occurred_at DESC);
CREATE INDEX idx_agent_activity_correlation
    ON agent_activities(correlation_id, occurred_at ASC);
