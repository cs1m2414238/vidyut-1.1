package com.vidyut.autopilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AutopilotDelayRequest {
    @Min(1)
    @Max(180)
    private int delayMinutes = 25;
}
