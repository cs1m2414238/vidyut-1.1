package com.vidyut.company.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record CompanyAgentActionResponse(
        String state,
        String message,
        CompanyAgentActionType action,
        Map<String, Object> result,
        LocalDateTime executedAt
) {}
