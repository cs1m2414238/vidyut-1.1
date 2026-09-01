package com.vidyut.autopilot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AutopilotStopSwapRequest {
    @NotNull
    private Long stationId;
}
