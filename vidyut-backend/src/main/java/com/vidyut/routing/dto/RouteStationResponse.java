package com.vidyut.routing.dto;

import com.vidyut.station.dto.StationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStationResponse {
    private StationResponse station;
    private double distanceFromOriginKm;
    private int recommendedChargeMinutes;
    private double detourKm;
    private int etaMinutes;
    private int availableSlots;
    private boolean connectorMatched;
    private double estimatedChargingCost;
    private String reason;
}
