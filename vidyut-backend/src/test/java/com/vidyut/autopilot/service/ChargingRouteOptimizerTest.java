package com.vidyut.autopilot.service;

import com.vidyut.routing.dto.OsrmTableResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargingRouteOptimizerTest {

    private final ChargingRouteOptimizer optimizer = new ChargingRouteOptimizer();

    @Test
    void skipsDestinationChargerWhenTheReserveIsAlreadySatisfied() {
        var charger = new ChargingRouteOptimizer.ChargingOption(7L, 90, 2,
                120, 18, 4, 4.6);
        OsrmTableResponse matrix = matrix(
                new Double[][]{
                        {0.0, 90_000.0, 100_000.0},
                        {null, 0.0, 10_000.0},
                        {null, null, 0.0}
                },
                new Double[][]{
                        {0.0, 4_500.0, 5_000.0},
                        {null, 0.0, 500.0},
                        {null, null, 0.0}
                });

        var result = optimizer.optimize(new ChargingRouteOptimizer.OptimizationRequest(
                List.of(charger), matrix, 50, 0.2, 60, 20, 1_000, "TIME",
                50, 0.90, chargingCurve(), null));

        assertThat(result.stops()).isEmpty();
        assertThat(result.routeDistanceKm()).isEqualTo(100);
        assertThat(result.arrivalBatteryPercent()).isEqualTo(20);
    }

    @Test
    void timeModeChoosesTheFasterFeasibleChargerInsteadOfTheFirstCandidate() {
        var slow = new ChargingRouteOptimizer.ChargingOption(1L, 100, 2,
                25, 12, 20, 4.8);
        var fast = new ChargingRouteOptimizer.ChargingOption(2L, 120, 3,
                150, 18, 0, 4.5);
        OsrmTableResponse matrix = matrix(
                new Double[][]{
                        {0.0, 100_000.0, 120_000.0, 200_000.0},
                        {null, 0.0, 30_000.0, 100_000.0},
                        {null, null, 0.0, 80_000.0},
                        {null, null, null, 0.0}
                },
                new Double[][]{
                        {0.0, 4_000.0, 4_800.0, 8_000.0},
                        {null, 0.0, 1_200.0, 4_000.0},
                        {null, null, 0.0, 3_200.0},
                        {null, null, null, 0.0}
                });

        var result = optimizer.optimize(new ChargingRouteOptimizer.OptimizationRequest(
                List.of(slow, fast), matrix, 60, 0.2, 60, 20, 1_000, "TIME",
                50, 0.90, chargingCurve(), null));

        assertThat(result.stops()).singleElement()
                .extracting(stop -> stop.option().stationId())
                .isEqualTo(2L);
    }

    @Test
    void integratesVehiclePowerEfficiencyAndSocTaperInsteadOfUsingChargerRating() {
        var request = new ChargingRouteOptimizer.OptimizationRequest(
                List.of(), matrix(new Double[][]{{0.0}}, new Double[][]{{0.0}}),
                40.5, 0.14, 60, 15, 1_000, "TIME",
                50, 0.90, chargingCurve(), null);

        var noida = optimizer.estimateCharge(request, 180, 40.3, 64);
        var mathura = optimizer.estimateCharge(request, 180, 15.6, 76);
        var gwalior = optimizer.estimateCharge(request, 180, 15.3, 55);

        assertThat(noida.minutes()).isEqualTo(14);
        assertThat(mathura.minutes()).isEqualTo(35);
        assertThat(gwalior.minutes()).isEqualTo(22);
        assertThat(noida.effectivePowerKw()).isBetween(43.0, 44.0);
    }

    @Test
    void rejectsAnOtherwiseSafeDestinationWhenItMissesTheHardDeadline() {
        var charger = new ChargingRouteOptimizer.ChargingOption(7L, 90, 2,
                120, 18, 4, 4.6);
        OsrmTableResponse matrix = matrix(
                new Double[][]{
                        {0.0, 90_000.0, 100_000.0},
                        {null, 0.0, 10_000.0},
                        {null, null, 0.0}
                },
                new Double[][]{
                        {0.0, 4_500.0, 5_000.0},
                        {null, 0.0, 500.0},
                        {null, null, 0.0}
                });

        assertThatThrownBy(() -> optimizer.optimize(new ChargingRouteOptimizer.OptimizationRequest(
                List.of(charger), matrix, 50, 0.2, 60, 20, 1_000, "COST",
                50, 0.90, chargingCurve(), 83)))
                .hasMessageContaining("No charger sequence");
    }

    private List<ChargingRouteOptimizer.ChargingCurvePoint> chargingCurve() {
        return List.of(
                new ChargingRouteOptimizer.ChargingCurvePoint(0, 60, 50),
                new ChargingRouteOptimizer.ChargingCurvePoint(60, 80, 40),
                new ChargingRouteOptimizer.ChargingCurvePoint(80, 100, 20));
    }

    private OsrmTableResponse matrix(Double[][] distances, Double[][] durations) {
        return new OsrmTableResponse("Ok", rows(distances), rows(durations));
    }

    private List<List<Double>> rows(Double[][] values) {
        return java.util.Arrays.stream(values)
                .map(row -> java.util.Arrays.asList(row))
                .toList();
    }
}
