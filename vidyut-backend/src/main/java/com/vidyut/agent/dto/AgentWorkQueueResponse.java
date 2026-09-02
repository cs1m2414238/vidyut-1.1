package com.vidyut.agent.dto;

import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkPriority;
import com.vidyut.agent.entity.AgentWorkStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AgentWorkQueueResponse(
        AgentWorkspace workspace,
        Map<AgentWorkStatus, Long> counts,
        List<WorkItem> items,
        LocalDateTime generatedAt
) {
    public record WorkItem(
            Long id,
            String idempotencyKey,
            String correlationId,
            String objectiveId,
            String actionBundleId,
            String requestId,
            String category,
            AgentWorkStatus status,
            AgentWorkPriority priority,
            String title,
            String detail,
            String whatHappened,
            String whyItMatters,
            String alreadyDone,
            String proposedAction,
            String approvalReason,
            String expectedImpact,
            String actionType,
            Map<String, Object> actionPayload,
            String resourceType,
            Long resourceId,
            String resultSummary,
            String failureReason,
            int retryCount,
            int maxRetries,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime completedAt,
            LocalDateTime preparedAt,
            LocalDateTime approvedAt,
            LocalDateTime executionStartedAt,
            LocalDateTime executedAt,
            LocalDateTime expiresAt
    ) {}
}
