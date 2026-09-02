package com.vidyut.agent.entity;

public enum AgentOutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    RETRYABLE_FAILURE,
    FAILED
}
