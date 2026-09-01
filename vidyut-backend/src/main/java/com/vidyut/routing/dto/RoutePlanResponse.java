package com.vidyut.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanResponse {
    private String origin;
    private String destination;
    private String tripPurpose;
    private String purposeSummary;
    private int pastExperiencesUsed;
    private double totalDistanceKm;
    private int totalDurationMinutes;
    private List<RouteStationResponse> recommendedChargingStops;
    private Long vehicleId;
    private double usableRangeKm;
    private double reserveBatteryPercent;
    private double estimatedArrivalBatteryPercent;
    private boolean destinationWithinRange;
    private String routeSource;
    private String externalMapsUrl;
}
