package com.vidyut.admin.dto;

import com.vidyut.admin.entity.IncidentSeverity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentCreateRequest(
        @NotNull Long connectorId,
        @NotNull IncidentSeverity severity,
        @NotBlank @Size(max = 1500) String reason,
        @Min(0) @Max(43200) int estimatedDowntimeMinutes
) {}
