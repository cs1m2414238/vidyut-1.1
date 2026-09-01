package com.vidyut.admin.dto;

import com.vidyut.admin.entity.IncidentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentUpdateRequest(
        @NotNull IncidentStatus status,
        @Size(max = 1500) String note,
        @Min(0) @Max(43200) Integer estimatedDowntimeMinutes
) {}
