package com.vidyut.autopilot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotProgressRequest {

    @DecimalMin(value = "0.1", message = "Battery drop must be at least 0.1%")
    @DecimalMax(value = "25", message = "Battery drop cannot exceed 25%")
    @Builder.Default
    private double batteryDropPercent = 6;
}
