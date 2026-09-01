package com.vidyut.routing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanRequest {

    @NotBlank(message = "Origin location is required")
    private String origin;

    @NotBlank(message = "Destination location is required")
    private String destination;

    private String tripPurpose;

    private double currentBatteryPercent;
    private Long vehicleId;
    private Double originLatitude;
    private Double originLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Double reserveBatteryPercent;
    private Double destinationDistanceKm;
}
