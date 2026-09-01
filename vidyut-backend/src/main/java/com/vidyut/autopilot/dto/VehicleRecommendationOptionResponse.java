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
public class VehicleRecommendationOptionResponse {
    private Long vehicleId;
    private String vehicleName;
    private String registrationNumber;
    private List<String> supportedConnectors;
    private double batteryCapacityKwh;
    private double currentBatteryPercent;
    private double efficiencyWhPerKm;
    private double maximumChargingPowerKw;
    private boolean feasible;
    private String reason;
    private int compatibleChargersEvaluated;
    private int chargingStops;
    private int journeyMinutes;
    private int chargingMinutes;
    private double estimatedCost;
    private double arrivalBatteryPercent;
    private boolean withinBudget;
    private boolean deadlineFeasible;
}
