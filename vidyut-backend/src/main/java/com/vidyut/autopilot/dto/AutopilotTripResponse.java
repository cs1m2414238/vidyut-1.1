package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.AutopilotTripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotTripResponse {
    private java.util.List<java.util.List<Double>> routeCoordinates;
    private Long id;
    private String idempotencyKey;
    private String goal;
    private String origin;
    private String destination;
    private String arrivalDeadline;
    private String optimizeFor;
    private String tripPurpose;
    private String memorySummary;
    private String autonomyMode;
    private double minimumArrivalBatteryPercent;
    private double maximumChargingBudget;
    private double totalDistanceKm;
    private double baseRouteDistanceKm;
    private double chargingDetourDistanceKm;
    private int estimatedDriveMinutes;
    private int baseDriveMinutes;
    private int chargingDetourMinutes;
    private int estimatedChargingMinutes;
    private int estimatedQueueMinutes;
    private int connectionOverheadMinutes;
    private int totalDurationMinutes;
    private double estimatedChargingCost;
    private double estimatedArrivalBatteryPercent;
    private int feasibleAlternativesCompared;
    private String optimizationSummary;
    private String routeEngine;
    private Long activeStationId;
    private Long activeBookingId;
    private AutopilotTripStatus status;
    private String paymentMessage;
    private double walletBalance;
    private AutopilotTelemetryResponse telemetry;
    private AutopilotRecoveryResponse recovery;
    private List<AutopilotStopResponse> stops;
    private List<AutopilotActionResponse> timeline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
