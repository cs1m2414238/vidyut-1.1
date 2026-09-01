package com.vidyut.admin.dto;

import java.util.List;
import java.util.Map;

public record AdminAgentResponse(
        String answer,
        List<Map<String, Object>> findings,
        List<Map<String, Object>> suggestedActions,
        String sourceOfTruth,
        boolean requiresApproval
) {}
