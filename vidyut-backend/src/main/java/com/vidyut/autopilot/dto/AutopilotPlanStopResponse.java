package com.vidyut.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotPlanStopResponse {
    private int sequenceNumber;
    private Long stationId;
    private String stationName;
    private String stationAddress;
    private String connectorType;
    private double powerKw;
    private double effectivePowerKw;
    private double distanceFromOriginKm;
    private double routeOffsetKm;
    private String estimatedArrivalTime;
    private String predictedSlotFreeAt;
    private String timingScore;
    private String timingLabel;
    private double arrivalBatteryPercent;
    private double targetBatteryPercent;
    private int estimatedWaitMinutes;
    private int chargingMinutes;
    private int connectionMinutes;
    private double estimatedCost;
    private boolean demoData;
    private int availableConnectors;
    private int queueCount;
    private double rating;
    private String selectionReason;
}
