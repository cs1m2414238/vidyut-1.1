CREATE TABLE agent_domain_events (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(180) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    actor_account_id BIGINT REFERENCES accounts(id),
    payload_json VARCHAR(5000) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_domain_event_type_time
    ON agent_domain_events(event_type, occurred_at DESC);
CREATE INDEX idx_agent_domain_event_aggregate
    ON agent_domain_events(aggregate_type, aggregate_id, occurred_at DESC);

CREATE TABLE agent_work_items (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    workspace VARCHAR(20) NOT NULL,
    work_key VARCHAR(180) NOT NULL,
    category VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    title VARCHAR(180) NOT NULL,
    detail VARCHAR(1500) NOT NULL,
    action_type VARCHAR(80),
    action_payload_json VARCHAR(5000),
    resource_type VARCHAR(50),
    resource_id BIGINT,
    source_event_id BIGINT REFERENCES agent_domain_events(id),
    result_summary VARCHAR(1500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_work_item_scope_key UNIQUE (account_id, workspace, work_key)
);

CREATE INDEX idx_agent_work_queue_scope
    ON agent_work_items(account_id, workspace, status, updated_at DESC);
CREATE INDEX idx_agent_work_queue_resource
    ON agent_work_items(resource_type, resource_id, status);
