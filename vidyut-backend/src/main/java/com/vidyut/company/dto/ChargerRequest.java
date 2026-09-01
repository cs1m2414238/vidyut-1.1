package com.vidyut.company.dto;

import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ConnectorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChargerRequest {
    @NotNull
    private Long stationId;
    @NotBlank
    private String chargerCode;
    @NotNull
    private ConnectorType connectorType;
    @Min(1) @Max(500)
    private double powerKw;
    private ChargerStatus status = ChargerStatus.ONLINE;
    private boolean maintenanceMode;
    private boolean impactApproved;
    private boolean syntheticDemo;
    private ChargerStatus expectedStatus;
    @jakarta.validation.constraints.Size(max = 500)
    private String faultReason;
    private String firmwareVersion;
    @Min(0) @Max(100)
    private int healthScore = 100;
}
