package com.vidyut.autopilot.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/** Persisted evidence for the proposal; never inferred from UI placeholders. */
@Data @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
public class AutopilotRecoveryResponse {
    private String planId;
    private Double remainingCost;
    private String state;
    private String incidentId;
    private String autonomyMode;
    private String strategy;
    private String agentProvider;
    private List<AutopilotStopResponse> proposedStops;
    private String reason;
    private LocalDateTime capturedAt;
    private LocalDateTime positionRecordedAt;
    private String positionSource;
    private Double currentLatitude;
    private Double currentLongitude;
    private double currentSoc;
    private double batteryCapacityKwh;
    private double efficiencyWhPerKm;
    private double reserveSoc;
    private double safetyMarginSoc;
    private double safeReachableDistanceKm;
    private List<String> compatibleConnectors;
    private Long failedStationId;
    private Long failedConnectorId;
    private Long bridgeConnectorId;
    private Double distanceToBridgeKm;
    private Double energyToBridgeKwh;
    private Double predictedArrivalSoc;
    private Double departureTargetSoc;
    private Double originalRemainingDistanceKm;
    private Integer originalRemainingMinutes;
    private Double newRemainingDistanceKm;
    private Integer newRemainingMinutes;
    private Double additionalDistanceKm;
    private Integer additionalMinutes;
    private Double additionalCost;
    private LocalDateTime estimatedArrivalTime;
    private String routeEngine;
}
