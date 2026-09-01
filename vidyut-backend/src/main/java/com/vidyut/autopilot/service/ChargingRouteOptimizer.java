package com.vidyut.autopilot.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.dto.OsrmTableResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChargingRouteOptimizer {

    private static final int CONNECTION_OVERHEAD_MINUTES = 4;
    private static final int MAX_CHARGE_PERCENT = 95;

    public OptimizationResult optimize(OptimizationRequest request) {
        return optimize(request, false, 0);
    }

    /** The first option is a mandatory, already reachable bridge. Its charge is
     * limited to the next selected leg plus reserve/margin, never a fixed 80%. */
    public OptimizationResult optimizeRecovery(OptimizationRequest request, double safetyMarginSoc) {
        if (request.chargers().isEmpty()) throw new BadRequestException("A reachable bridge charger is required");
        return optimize(request, true, safetyMarginSoc);
    }

    private OptimizationResult optimize(OptimizationRequest request, boolean recovery, double safetyMarginSoc) {
        validate(request);
        int destinationIndex = request.chargers().size() + 1;
        List<Map<Integer, Label>> labels = new ArrayList<>();
        for (int index = 0; index <= destinationIndex; index++) {
            labels.add(new HashMap<>());
        }

        Label origin = Label.origin(request.startingBatteryPercent());
        labels.get(0).put(socKey(origin.departureBatteryPercent), origin);
        Label bestDestination = null;
        int transitionsEvaluated = 0;
        int feasibleAlternatives = 0;

        for (int fromIndex = 0; fromIndex < destinationIndex; fromIndex++) {
            for (Label current : List.copyOf(labels.get(fromIndex).values())) {
                for (int toIndex = fromIndex + 1; toIndex <= destinationIndex; toIndex++) {
                    if (recovery && fromIndex == 0 && toIndex != 1) continue;
                    transitionsEvaluated++;
                    Double distanceKm = matrixValue(request.matrix().distances(), fromIndex, toIndex, 1000.0);
                    Double durationMinutes = matrixValue(request.matrix().durations(), fromIndex, toIndex, 60.0);
                    if (distanceKm == null || durationMinutes == null || distanceKm < 0 || durationMinutes < 0) {
                        continue;
                    }

                    double energyUsedKwh = distanceKm * request.energyPerKmKwh();
                    double batteryUsedPercent = energyUsedKwh / request.batteryCapacityKwh() * 100.0;
                    double arrivalBattery = current.departureBatteryPercent - batteryUsedPercent;
                    if (arrivalBattery + 0.0001 < request.minimumBatteryPercent()) {
                        continue;
                    }
                    if (recovery && fromIndex == 1) {
                        double bridgeArrival = request.startingBatteryPercent()
                                - matrixValue(request.matrix().distances(), 0, 1, 1000.0)
                                * request.energyPerKmKwh() / request.batteryCapacityKwh() * 100.0;
                        double minimumDeparture = Math.ceil(Math.max(bridgeArrival,
                                request.minimumBatteryPercent() + safetyMarginSoc + batteryUsedPercent));
                        if (Math.abs(current.departureBatteryPercent - minimumDeparture) > 0.0001) continue;
                    }

                    int drivingMinutes = (int) Math.ceil(durationMinutes);
                    if (toIndex == destinationIndex) {
                        Label destination = current.arriveAtDestination(
                                arrivalBattery, distanceKm, drivingMinutes);
                        if (request.maximumTotalMinutes() != null
                                && destination.totalMinutes() > request.maximumTotalMinutes()) {
                            continue;
                        }
                        feasibleAlternatives++;
                        if (better(destination, bestDestination, request.optimizeFor())) {
                            bestDestination = destination;
                        }
                        continue;
                    }

                    ChargingOption option = request.chargers().get(toIndex - 1);
                    int minimumTarget = Math.max(
                            (int) Math.ceil(arrivalBattery + 1),
                            (int) Math.ceil(request.minimumBatteryPercent() + 2));
                    if (recovery && toIndex == 1) minimumTarget = (int) Math.ceil(arrivalBattery);
                    for (int targetBattery = minimumTarget;
                         targetBattery <= MAX_CHARGE_PERCENT;
                         targetBattery++) {
                        double energyAddedKwh = request.batteryCapacityKwh()
                                * (targetBattery - arrivalBattery) / 100.0;
                        ChargeEstimate chargeEstimate = estimateCharge(
                                request,
                                option.powerKw(),
                                arrivalBattery,
                                targetBattery);
                        double chargingCost = energyAddedKwh * option.pricePerKwh();
                        double totalCost = current.cost + chargingCost;
                        if (totalCost > request.maximumBudget() + 0.001) {
                            continue;
                        }

                        StopDecision decision = new StopDecision(
                                option,
                                round(arrivalBattery),
                                targetBattery,
                                round(energyAddedKwh),
                                chargeEstimate.minutes(),
                                round(chargeEstimate.effectivePowerKw()),
                                option.waitMinutes(),
                                CONNECTION_OVERHEAD_MINUTES,
                                roundMoney(chargingCost)
                        );
                        Label next = current.chargeAt(
                                targetBattery,
                                distanceKm,
                                drivingMinutes,
                                decision
                        );
                        Map<Integer, Label> nodeLabels = labels.get(toIndex);
                        int key = socKey(targetBattery);
                        Label existing = nodeLabels.get(key);
                        if (better(next, existing, request.optimizeFor())) {
                            nodeLabels.put(key, next);
                        }
                    }
                }
            }
        }

        if (bestDestination == null) {
            throw new BadRequestException(
                    "No charger sequence can satisfy the battery reserve and charging budget on this route");
        }

        List<StopDecision> stops = new ArrayList<>();
        Label cursor = bestDestination;
        while (cursor != null) {
            if (cursor.stopDecision != null) {
                stops.add(cursor.stopDecision);
            }
            cursor = cursor.previous;
        }
        Collections.reverse(stops);

        return new OptimizationResult(
                List.copyOf(stops),
                round(bestDestination.distanceKm),
                bestDestination.drivingMinutes,
                bestDestination.chargingMinutes,
                bestDestination.queueMinutes,
                bestDestination.connectionMinutes,
                roundMoney(bestDestination.cost),
                round(bestDestination.departureBatteryPercent),
                transitionsEvaluated,
                feasibleAlternatives
        );
    }

    private boolean better(Label candidate, Label current, String optimizeFor) {
        if (candidate == null) return false;
        if (current == null) return true;
        int comparison = switch (normalizeMode(optimizeFor)) {
            case "COST" -> compare(candidate.cost, current.cost,
                    candidate.totalMinutes(), current.totalMinutes());
            case "BALANCED" -> compare(
                    candidate.totalMinutes() + candidate.cost * 0.22 + candidate.reliabilityPenalty,
                    current.totalMinutes() + current.cost * 0.22 + current.reliabilityPenalty,
                    candidate.cost,
                    current.cost);
            default -> compare(candidate.totalMinutes(), current.totalMinutes(),
                    candidate.cost, current.cost);
        };
        return comparison < 0;
    }

    private int compare(double firstPrimary, double secondPrimary,
                        double firstSecondary, double secondSecondary) {
        int primary = Double.compare(firstPrimary, secondPrimary);
        return primary != 0 ? primary : Double.compare(firstSecondary, secondSecondary);
    }

    private String normalizeMode(String optimizeFor) {
        return optimizeFor == null ? "TIME" : optimizeFor.trim().toUpperCase();
    }

    private Double matrixValue(List<List<Double>> matrix, int row, int column, double divisor) {
        if (matrix == null || row < 0 || row >= matrix.size()) return null;
        List<Double> values = matrix.get(row);
        if (values == null || column < 0 || column >= values.size()) return null;
        Double value = values.get(column);
        return value == null || !Double.isFinite(value) ? null : value / divisor;
    }

    private int socKey(double batteryPercent) {
        return (int) Math.round(batteryPercent * 10);
    }

    private void validate(OptimizationRequest request) {
        if (request == null || request.matrix() == null) {
            throw new IllegalArgumentException("A route matrix is required");
        }
        if (!Double.isFinite(request.batteryCapacityKwh()) || !Double.isFinite(request.energyPerKmKwh())
                || request.batteryCapacityKwh() <= 0 || request.energyPerKmKwh() <= 0) {
            throw new IllegalArgumentException("A valid vehicle energy model is required");
        }
        if (!Double.isFinite(request.startingBatteryPercent()) || !Double.isFinite(request.minimumBatteryPercent())
                || request.startingBatteryPercent() > 100 || request.minimumBatteryPercent() < 0
                || request.startingBatteryPercent() <= request.minimumBatteryPercent()) {
            throw new IllegalArgumentException("Starting battery must be above the reserve");
        }
        if (request.vehicleMaxChargePowerKw() <= 0
                || request.chargingEfficiency() <= 0
                || request.chargingEfficiency() > 1) {
            throw new IllegalArgumentException("A valid vehicle charging profile is required");
        }
        if (request.chargingCurve() == null || request.chargingCurve().isEmpty()) {
            throw new IllegalArgumentException("At least one charging-curve segment is required");
        }
    }

    ChargeEstimate estimateCharge(
            OptimizationRequest request,
            double chargerRatedPowerKw,
            double startingSoc,
            double targetSoc
    ) {
        return estimateCharge(
                request.batteryCapacityKwh(),
                chargerRatedPowerKw,
                request.vehicleMaxChargePowerKw(),
                request.chargingEfficiency(),
                request.chargingCurve(),
                startingSoc,
                targetSoc);
    }

    ChargeEstimate estimateCharge(
            double batteryCapacityKwh,
            double chargerRatedPowerKw,
            double vehicleMaxChargePowerKw,
            double chargingEfficiency,
            List<ChargingCurvePoint> chargingCurve,
            double startingSoc,
            double targetSoc
    ) {
        double exactMinutes = 0;
        double energyAddedKwh = 0;
        for (ChargingCurvePoint segment : chargingCurve) {
            double segmentStart = Math.max(startingSoc, segment.socFrom());
            double segmentEnd = Math.min(targetSoc, segment.socTo());
            if (segmentEnd <= segmentStart) continue;

            double segmentEnergyKwh = batteryCapacityKwh
                    * (segmentEnd - segmentStart) / 100.0;
            double batterySidePowerKw = Math.min(
                    Math.min(chargerRatedPowerKw, vehicleMaxChargePowerKw),
                    segment.effectivePowerKw()) * chargingEfficiency;
            if (batterySidePowerKw <= 0) {
                throw new IllegalArgumentException("Charging-curve power must be positive");
            }
            exactMinutes += segmentEnergyKwh / batterySidePowerKw * 60.0;
            energyAddedKwh += segmentEnergyKwh;
        }
        double expectedEnergyKwh = batteryCapacityKwh
                * (targetSoc - startingSoc) / 100.0;
        if (energyAddedKwh + 0.001 < expectedEnergyKwh) {
            throw new IllegalArgumentException("Charging curve must cover the requested state-of-charge range");
        }
        int minutes = Math.max(1, (int) Math.ceil(exactMinutes));
        double effectivePowerKw = exactMinutes <= 0
                ? 0
                : energyAddedKwh / (exactMinutes / 60.0);
        return new ChargeEstimate(minutes, effectivePowerKw);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record OptimizationRequest(
            List<ChargingOption> chargers,
            OsrmTableResponse matrix,
            double batteryCapacityKwh,
            double energyPerKmKwh,
            double startingBatteryPercent,
            double minimumBatteryPercent,
            double maximumBudget,
            String optimizeFor,
            double vehicleMaxChargePowerKw,
            double chargingEfficiency,
            List<ChargingCurvePoint> chargingCurve,
            Integer maximumTotalMinutes
    ) {
    }

    public record ChargingCurvePoint(
            double socFrom,
            double socTo,
            double effectivePowerKw
    ) {
    }

    record ChargeEstimate(int minutes, double effectivePowerKw) {
    }

    public record ChargingOption(
            Long stationId,
            double routeProgressKm,
            double routeOffsetKm,
            double powerKw,
            double pricePerKwh,
            int waitMinutes,
            double rating
    ) {
    }

    public record StopDecision(
            ChargingOption option,
            double arrivalBatteryPercent,
            double targetBatteryPercent,
            double energyAddedKwh,
            int chargingMinutes,
            double effectivePowerKw,
            int queueMinutes,
            int connectionMinutes,
            double cost
    ) {
    }

    public record OptimizationResult(
            List<StopDecision> stops,
            double routeDistanceKm,
            int drivingMinutes,
            int chargingMinutes,
            int queueMinutes,
            int connectionMinutes,
            double cost,
            double arrivalBatteryPercent,
            int transitionsEvaluated,
            int feasibleAlternatives
    ) {
        public int totalMinutes() {
            return drivingMinutes + chargingMinutes + queueMinutes + connectionMinutes;
        }
    }

    private static final class Label {
        private final double departureBatteryPercent;
        private final double distanceKm;
        private final int drivingMinutes;
        private final int chargingMinutes;
        private final int queueMinutes;
        private final int connectionMinutes;
        private final double cost;
        private final double reliabilityPenalty;
        private final Label previous;
        private final StopDecision stopDecision;

        private Label(double departureBatteryPercent, double distanceKm, int drivingMinutes,
                      int chargingMinutes, int queueMinutes, int connectionMinutes,
                      double cost, double reliabilityPenalty, Label previous,
                      StopDecision stopDecision) {
            this.departureBatteryPercent = departureBatteryPercent;
            this.distanceKm = distanceKm;
            this.drivingMinutes = drivingMinutes;
            this.chargingMinutes = chargingMinutes;
            this.queueMinutes = queueMinutes;
            this.connectionMinutes = connectionMinutes;
            this.cost = cost;
            this.reliabilityPenalty = reliabilityPenalty;
            this.previous = previous;
            this.stopDecision = stopDecision;
        }

        private static Label origin(double startingBatteryPercent) {
            return new Label(startingBatteryPercent, 0, 0, 0, 0, 0,
                    0, 0, null, null);
        }

        private Label chargeAt(double targetBatteryPercent, double legDistanceKm,
                               int legDrivingMinutes, StopDecision decision) {
            double ratingPenalty = Math.max(0, 5 - decision.option().rating()) * 4;
            return new Label(
                    targetBatteryPercent,
                    distanceKm + legDistanceKm,
                    drivingMinutes + legDrivingMinutes,
                    chargingMinutes + decision.chargingMinutes(),
                    queueMinutes + decision.queueMinutes(),
                    connectionMinutes + decision.connectionMinutes(),
                    cost + decision.cost(),
                    reliabilityPenalty + ratingPenalty,
                    this,
                    decision
            );
        }

        private Label arriveAtDestination(double arrivalBatteryPercent, double legDistanceKm,
                                          int legDrivingMinutes) {
            return new Label(
                    arrivalBatteryPercent,
                    distanceKm + legDistanceKm,
                    drivingMinutes + legDrivingMinutes,
                    chargingMinutes,
                    queueMinutes,
                    connectionMinutes,
                    cost,
                    reliabilityPenalty,
                    this,
                    null
            );
        }

        private int totalMinutes() {
            return drivingMinutes + chargingMinutes + queueMinutes + connectionMinutes;
        }
    }
}
