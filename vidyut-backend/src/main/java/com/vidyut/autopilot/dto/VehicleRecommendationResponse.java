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
public class VehicleRecommendationResponse {
    private Long recommendedVehicleId;
    private String recommendedVehicleName;
    private String reason;
    private String origin;
    private String destination;
    private String optimizeFor;
    private AutopilotPlanResponse recommendedPlan;
    private List<VehicleRecommendationOptionResponse> vehicles;
}
