package com.vidyut.agent.dto;

import com.vidyut.agent.entity.AgentWorkspace;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AgentActivityResponse(
        AgentWorkspace workspace,
        List<Activity> activities,
        LocalDateTime generatedAt
) {
    public record Activity(
            Long id,
            Long workItemId,
            String correlationId,
            String activityType,
            String summary,
            String detail,
            Map<String, Object> metadata,
            LocalDateTime occurredAt
    ) {}
}
