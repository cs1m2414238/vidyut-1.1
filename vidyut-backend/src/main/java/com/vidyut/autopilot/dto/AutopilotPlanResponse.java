package com.vidyut.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotPlanResponse {
    private Long vehicleId;
    private String vehicleName;
    private String registrationNumber;
    private String connectorType;
    private String origin;
    private String destination;
    private String arrivalDeadline;
    private String estimatedArrivalTime;
    private String optimizeFor;
    private String tripPurpose;
    private String purposeSummary;
    private int pastExperiencesUsed;
    private String memorySummary;
    private String autonomyMode;
    private double currentBatteryPercent;
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
    private double budgetRemaining;
    private double estimatedArrivalBatteryPercent;
    private double batteryCapacityKwh;
    private double availableEnergyKwh;
    private double energyConsumptionKwhPer100Km;
    private double vehicleMaxChargingPowerKw;
    private double chargingEfficiencyPercent;
    private int dbBoundCandidatesEvaluated;
    private int postCorridorCandidatesEvaluated;
    private int compatibleChargersEvaluated;
    private int feasibleAlternativesCompared;
    private String optimizationSummary;
    private String routeEngine;
    private boolean withinBudget;
    private boolean safeArrivalReserve;
    private boolean deadlineFeasible;
    private boolean overallFeasible;
    private int deadlineMinutesLate;
    private boolean liveAvailabilityChecked;
    private boolean confirmationRequired;
    private List<AutopilotPlanStopResponse> stops;
}
