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
            String category,
            AgentWorkStatus status,
            AgentWorkPriority priority,
            String title,
            String detail,
            String actionType,
            Map<String, Object> actionPayload,
            String resourceType,
            Long resourceId,
            String resultSummary,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime completedAt
    ) {}
}
