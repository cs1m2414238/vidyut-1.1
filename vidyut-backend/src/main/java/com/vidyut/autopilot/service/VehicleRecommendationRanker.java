package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.VehicleRecommendationOptionResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class VehicleRecommendationRanker {

    public Optional<VehicleRecommendationOptionResponse> recommended(
            List<VehicleRecommendationOptionResponse> vehicles,
            String optimization
    ) {
        Optional<VehicleRecommendationOptionResponse> strictlyFeasible = vehicles.stream()
                .filter(VehicleRecommendationOptionResponse::isFeasible)
                .min(comparator(optimization));
        if (strictlyFeasible.isPresent()) {
            return strictlyFeasible;
        }
        return vehicles.stream()
                .filter(v -> v.getChargingStops() > 0 || v.getJourneyMinutes() > 0)
                .min(comparator(optimization));
    }

    public Comparator<VehicleRecommendationOptionResponse> comparator(String optimization) {
        String normalized = optimization == null ? "TIME" : optimization.trim().toUpperCase();
        return switch (normalized) {
            case "COST" -> Comparator
                    .comparingDouble(VehicleRecommendationOptionResponse::getEstimatedCost)
                    .thenComparingInt(VehicleRecommendationOptionResponse::getJourneyMinutes)
                    .thenComparingInt(VehicleRecommendationOptionResponse::getChargingStops);
            case "BALANCED" -> Comparator
                    .comparingDouble((VehicleRecommendationOptionResponse vehicle) ->
                            vehicle.getJourneyMinutes()
                                    + vehicle.getEstimatedCost() * 0.22
                                    + vehicle.getChargingStops() * 5.0)
                    .thenComparingInt(VehicleRecommendationOptionResponse::getJourneyMinutes);
            default -> Comparator
                    .comparingInt(VehicleRecommendationOptionResponse::getJourneyMinutes)
                    .thenComparingInt(VehicleRecommendationOptionResponse::getChargingStops)
                    .thenComparingDouble(VehicleRecommendationOptionResponse::getEstimatedCost);
        };
    }
}
