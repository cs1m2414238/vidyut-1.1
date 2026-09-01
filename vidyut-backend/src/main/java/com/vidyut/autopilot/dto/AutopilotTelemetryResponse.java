package com.vidyut.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotTelemetryResponse {
    private Long vehicleId;
    private String vehicleName;
    private String registrationNumber;
    private String connectorType;
    private double batteryCapacityKwh;
    private double batteryPercent;
    private double remainingRangeKm;
    private String state;
    private Double latitude;
    private Double longitude;
    private java.time.LocalDateTime positionRecordedAt;
    private String positionSource;
    private double distanceTravelledKm;
    private double safeReachableDistanceKm;
}
