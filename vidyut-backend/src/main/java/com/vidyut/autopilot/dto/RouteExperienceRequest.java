package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.RouteExperienceOutcome;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RouteExperienceRequest(
        Long stationId,
        @NotNull RouteExperienceOutcome outcome,
        @Size(max = 1200) String detail,
        @Min(1) @Max(5) Integer rating,
        @Min(0) @Max(1440) Integer delayMinutes
) {}
