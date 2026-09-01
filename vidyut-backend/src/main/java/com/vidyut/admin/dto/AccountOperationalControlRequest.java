package com.vidyut.admin.dto;

import com.vidyut.admin.entity.OperationalControlType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountOperationalControlRequest(
        @NotNull OperationalControlType control,
        boolean enabled,
        @NotBlank String reason,
        @Min(1) @Max(168) Integer durationHours
) {
}
