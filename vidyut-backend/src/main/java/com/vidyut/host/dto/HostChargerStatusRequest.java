package com.vidyut.host.dto;

import com.vidyut.station.entity.ChargerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HostChargerStatusRequest {
    @NotNull private ChargerStatus status;
    @Min(0) private double currentPowerKw;
    @Min(0) private double sessionEnergyKwh;
    @Min(0) @Max(100) private int healthScore = 100;
    private String faultCode;
    private boolean impactApproved;
}
