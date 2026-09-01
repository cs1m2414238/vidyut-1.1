package com.vidyut.company.dto;

import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ConnectorType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChargerResponse {
    private Long id;
    private Long stationId;
    private String stationName;
    private String chargerCode;
    private ConnectorType connectorType;
    private double powerKw;
    private boolean available;
    private ChargerStatus status;
    private boolean maintenanceMode;
    private String firmwareVersion;
    private int healthScore;
    private LocalDateTime lastHeartbeat;
    private String faultCode;
    private String faultReason;
    private String statusSource;
    private boolean demoData;
}
