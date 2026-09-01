package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.VehicleRecommendationOptionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleRecommendationRankerTest {

    private final VehicleRecommendationRanker ranker = new VehicleRecommendationRanker();

    @Test
    void fastestCostAndBalancedRemainIndependentRankingStrategies() {
        VehicleRecommendationOptionResponse fast = option(1, true, 400, 680, 1);
        VehicleRecommendationOptionResponse value = option(2, true, 425, 510, 2);
        VehicleRecommendationOptionResponse cheap = option(3, true, 450, 470, 1);

        assertThat(ranker.recommended(List.of(fast, value, cheap), "TIME"))
                .get().extracting(VehicleRecommendationOptionResponse::getVehicleId).isEqualTo(1L);
        assertThat(ranker.recommended(List.of(fast, value, cheap), "COST"))
                .get().extracting(VehicleRecommendationOptionResponse::getVehicleId).isEqualTo(3L);
        assertThat(ranker.recommended(List.of(fast, value, cheap), "BALANCED"))
                .get().extracting(VehicleRecommendationOptionResponse::getVehicleId).isEqualTo(2L);
    }

    @Test
    void infeasibleVehicleCannotWinEvenWhenItsMetricsLookBest() {
        VehicleRecommendationOptionResponse infeasible = option(7, false, 300, 100, 0);
        VehicleRecommendationOptionResponse feasible = option(8, true, 430, 550, 1);

        assertThat(ranker.recommended(List.of(infeasible, feasible), "TIME"))
                .get().extracting(VehicleRecommendationOptionResponse::getVehicleId).isEqualTo(8L);
    }

    private VehicleRecommendationOptionResponse option(
            long vehicleId,
            boolean feasible,
            int minutes,
            double cost,
            int stops
    ) {
        return VehicleRecommendationOptionResponse.builder()
                .vehicleId(vehicleId)
                .feasible(feasible)
                .journeyMinutes(minutes)
                .estimatedCost(cost)
                .chargingStops(stops)
                .build();
    }
}
