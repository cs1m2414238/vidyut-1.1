package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.AutopilotActionResponse;
import com.vidyut.autopilot.dto.AutopilotPlanResponse;
import com.vidyut.autopilot.dto.AutopilotPlanStopResponse;
import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotStopResponse;
import com.vidyut.autopilot.dto.AutopilotTelemetryResponse;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.dto.AutopilotTripSummaryResponse;
import com.vidyut.autopilot.dto.RouteExperienceRequest;
import com.vidyut.autopilot.dto.RouteExperienceResponse;
import com.vidyut.autopilot.dto.VehicleRecommendationOptionResponse;
import com.vidyut.autopilot.dto.VehicleRecommendationRequest;
import com.vidyut.autopilot.dto.VehicleRecommendationResponse;
import com.vidyut.autopilot.entity.AutopilotAction;
import com.vidyut.autopilot.entity.AutopilotActionState;
import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.entity.RouteExperience;
import com.vidyut.autopilot.entity.RouteExperienceOutcome;
import com.vidyut.autopilot.entity.TripPurpose;
import com.vidyut.autopilot.repository.AutopilotActionRepository;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.autopilot.repository.RouteExperienceRepository;
import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.dto.PaymentRequest;
import com.vidyut.payment.dto.PaymentResponse;
import com.vidyut.payment.service.PaymentService;
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmGeometry;
import com.vidyut.routing.dto.OsrmResponse;
import com.vidyut.routing.dto.OsrmRoute;
import com.vidyut.routing.dto.OsrmTableResponse;
import com.vidyut.routing.dto.RouteStationResponse;
import com.vidyut.routing.exception.OsrmException;
import com.vidyut.routing.service.LocationResolver;
import com.vidyut.routing.service.RouteCorridorService;
import com.vidyut.routing.service.RoutingService;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutopilotService {

    private static final double ENERGY_PER_KM_KWH = 0.14;
    private static final int MAX_STOPS = 20;

    private final AutopilotTripRepository tripRepository;
    private final AutopilotStopRepository stopRepository;
    private final AutopilotActionRepository actionRepository;
    private final RouteExperienceRepository experienceRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingStationRepository stationRepository;
    private final BookingService bookingService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final RoutingService routingService;
    private final OsrmClient osrmClient;
    private final LocationResolver locationResolver;
    private final RouteCorridorService routeCorridorService;
    private final ChargingRouteOptimizer chargingRouteOptimizer;
    private final VehicleChargingProfileService vehicleChargingProfileService;
    private final VehicleRecommendationRanker vehicleRecommendationRanker;
    private final DeadlineEvaluator deadlineEvaluator;
    private final SafeRecoveryPlanner recoveryPlanner;
    private final AutopilotPositionService positions;
    private final RecoveryStore recoveryStore;
    private final com.vidyut.station.repository.ChargingConnectorRepository recoveryConnectors;

    @Value("${vidyut.routing.corridor-time-km:8}")
    private double timeCorridorKm;

    @Value("${vidyut.routing.corridor-balanced-km:12}")
    private double balancedCorridorKm;

    @Value("${vidyut.routing.corridor-cost-km:20}")
    private double costCorridorKm;

    @Value("${vidyut.routing.stop-endpoint-buffer-km:10}")
    private double stopEndpointBufferKm;

    @Value("${vidyut.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    @Value("${vidyut.autopilot.current-trip-max-age-hours:72}")
    private long currentTripMaxAgeHours;

    private OsrmRoute getOsrmRoute(Coordinate origin, Coordinate destination) {
        return getOsrmRoute(List.of(origin, destination));
    }

    private OsrmRoute getOsrmRoute(List<Coordinate> waypoints) {
        return getOsrmRoute(waypoints, OsrmClient.RouteEngine.PRIMARY);
    }

    private OsrmRoute getOsrmRoute(List<Coordinate> waypoints, OsrmClient.RouteEngine engine) {
        OsrmResponse response;
        try {
            response = osrmClient.getRoute(waypoints, engine);
        } catch (OsrmException exception) {
            if (exception.isLocationOutsideCoverage()) {
                throw new BadRequestException(
                        "The origin or destination is outside the configured OpenStreetMap coverage");
            }
            throw exception;
        }
        if (response == null || !"Ok".equals(response.code())
                || response.routes() == null || response.routes().isEmpty()) {
            throw new BadRequestException(
                    "No drivable route was found in the configured OpenStreetMap coverage");
        }
        return response.routes().get(0);
    }

    private BaseRoute getBestBaseRoute(Coordinate origin, Coordinate destination) {
        return getBestRoute(List.of(origin, destination));
    }

    private BaseRoute getBestRoute(List<Coordinate> waypoints) {
        OsrmClient.RouteSelection selection = osrmClient.getBestRoute(waypoints);
        OsrmResponse response = selection.response();
        if (response == null || !"Ok".equals(response.code())
                || response.routes() == null || response.routes().isEmpty()) {
            throw new BadRequestException("No drivable base route was found for this journey");
        }
        OsrmRoute route = response.routes().get(0);
        if (route.geometry() == null || route.geometry().coordinates() == null
                || route.geometry().coordinates().size() < 2) {
            throw new BadRequestException("The route engine did not return a usable base-route polyline");
        }
        if (route.legs() == null || route.legs().size() != waypoints.size() - 1) {
            throw new BadRequestException("The route engine did not return every journey leg");
        }
        return new BaseRoute(route, selection.engine());
    }

    @Transactional(readOnly = true)
    public VehicleRecommendationResponse recommendVehicle(
            Long userId,
            VehicleRecommendationRequest request
    ) {
        List<Vehicle> vehicles = vehicleRepository.findByUserId(userId);
        if (vehicles.isEmpty()) {
            throw new BadRequestException("Add at least one EV before asking Vidyut to choose a vehicle");
        }

        String optimization = normalizedOptimization(request.getOptimizeFor());
        List<VehicleEvaluation> evaluations = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            double currentBattery = vehicle.getBatteryPercent() != null
                    ? vehicle.getBatteryPercent()
                    : request.getFallbackBatteryPercent();
            if (currentBattery <= request.getMinimumArrivalBatteryPercent()) {
                evaluations.add(new VehicleEvaluation(vehicle, null,
                        recommendationOption(vehicle, currentBattery, null, false,
                                "Current battery is not above the requested "
                                        + round(request.getMinimumArrivalBatteryPercent()) + "% reserve.")));
                continue;
            }

            AutopilotTripRequest tripRequest = AutopilotTripRequest.builder()
                    .vehicleId(vehicle.getId())
                    .origin(request.getOrigin())
                    .destination(request.getDestination())
                    .goal(request.getGoal())
                    .tripPurpose(request.getTripPurpose())
                    .arrivalDeadline(request.getArrivalDeadline())
                    .optimizeFor(optimization)
                    .autonomyMode(request.getAutonomyMode())
                    .currentBatteryPercent(currentBattery)
                    .minimumArrivalBatteryPercent(request.getMinimumArrivalBatteryPercent())
                    .maximumChargingBudget(request.getMaximumChargingBudget())
                    .idempotencyKey("VEHICLE-COMPARISON-" + vehicle.getId() + "-" + UUID.randomUUID())
                    .build();
            try {
                AutopilotPlanResponse plan = previewTrip(userId, tripRequest);
                boolean feasible = plan.isOverallFeasible();
                evaluations.add(new VehicleEvaluation(vehicle, plan,
                        recommendationOption(vehicle, currentBattery, plan, feasible,
                                feasible ? feasibleVehicleReason(plan, optimization)
                                        : infeasiblePlanReason(plan))));
            } catch (BadRequestException planningFailure) {
                evaluations.add(new VehicleEvaluation(vehicle, null,
                        recommendationOption(vehicle, currentBattery, null, false,
                                planningFailureReason(vehicle, planningFailure))));
            }
        }

        Comparator<VehicleEvaluation> ranking = Comparator.comparing(
                VehicleEvaluation::option,
                vehicleRecommendationRanker.comparator(optimization));
        VehicleRecommendationOptionResponse recommendedOption = vehicleRecommendationRanker.recommended(
                evaluations.stream().map(VehicleEvaluation::option).toList(), optimization).orElse(null);
        VehicleEvaluation recommended = recommendedOption == null ? null : evaluations.stream()
                .filter(evaluation -> evaluation.option().getVehicleId().equals(recommendedOption.getVehicleId()))
                .findFirst()
                .orElse(null);

        if (recommended != null) {
            String reason = recommended.plan() != null && recommended.plan().isOverallFeasible()
                    ? recommendedVehicleReason(recommended.plan(), optimization)
                    : (recommended.plan() != null
                            ? "Best match: " + infeasiblePlanReason(recommended.plan())
                            : recommended.option().getReason());
            recommended.option().setReason(reason);
        }
        List<VehicleRecommendationOptionResponse> options = evaluations.stream()
                .sorted((first, second) -> {
                    if (first == recommended) return -1;
                    if (second == recommended) return 1;
                    if (first.option().isFeasible() != second.option().isFeasible()) {
                        return first.option().isFeasible() ? -1 : 1;
                    }
                    return first.option().isFeasible()
                            ? ranking.compare(first, second)
                            : first.vehicle().getMakeAndModel().compareToIgnoreCase(second.vehicle().getMakeAndModel());
                })
                .map(VehicleEvaluation::option)
                .toList();

        String reason = recommended == null
                ? "None of the saved vehicles satisfies connector reachability, battery reserve, budget and deadline together."
                : recommended.option().getReason();
        return VehicleRecommendationResponse.builder()
                .recommendedVehicleId(recommended == null ? null : recommended.vehicle().getId())
                .recommendedVehicleName(recommended == null ? null : recommended.vehicle().getMakeAndModel())
                .reason(reason)
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .optimizeFor(optimization)
                .recommendedPlan(recommended == null ? null : recommended.plan())
                .vehicles(options)
                .build();
    }

    @Transactional(readOnly = true)
    public AutopilotPlanResponse previewTrip(Long userId, AutopilotTripRequest request) {
        Vehicle vehicle = ownedVehicle(request.getVehicleId(), userId);
        validateConstraints(request);
        LocalTime departure = LocalTime.now().withSecond(0).withNano(0);
        DeadlineEvaluator.DeadlineAssessment deadlineWindow = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, 0);
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        Coordinate origin = locationResolver.resolve(request.getOrigin());
        Coordinate destination = locationResolver.resolve(request.getDestination());
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        String autonomyMode = normalizedAutonomyMode(request.getAutonomyMode());
        TripPurpose purpose = resolvedPurpose(request);
        RouteMemory memory = routeMemory(request.getOrigin(), request.getDestination());
        PlanningContext plan = planJourney(
                vehicle, request, origin, destination, capacityKwh,
                optimization, purpose, memory, chargingProfile,
                deadlineWindow.availableMinutes());

        double baseDistanceKm = round(plan.baseRoute().route().distance() / 1000.0);
        double totalDistanceKm = round(plan.finalRoute().distance() / 1000.0);
        double detourDistanceKm = round(Math.max(0, totalDistanceKm - baseDistanceKm));
        int baseDriveMinutes = (int) Math.ceil(plan.baseRoute().route().duration() / 60.0);
        int driveMinutes = (int) Math.ceil(plan.finalRoute().duration() / 60.0);
        int detourMinutes = Math.max(0, driveMinutes - baseDriveMinutes);
        ChargingRouteOptimizer.OptimizationResult optimized = plan.optimized();
        int totalMinutes = driveMinutes + optimized.chargingMinutes()
                + optimized.queueMinutes() + optimized.connectionMinutes();
        DeadlineEvaluator.DeadlineAssessment deadlineAssessment = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, totalMinutes);
        boolean withinBudget = optimized.cost() <= request.getMaximumChargingBudget();
        boolean safeArrivalReserve = optimized.arrivalBatteryPercent()
                >= request.getMinimumArrivalBatteryPercent();
        boolean overallFeasible = withinBudget && safeArrivalReserve && deadlineAssessment.feasible();
        String summary = optimizationSummary(
                optimization, optimized, plan.candidates().size(), plan.corridorKm(), plan);
        if (!deadlineAssessment.feasible()) {
            summary += " The battery-safe preview misses the requested arrival deadline by "
                    + deadlineAssessment.minutesLate() + " minutes.";
        }

        AutopilotTrip proposal = AutopilotTrip.builder()
                .vehicleId(vehicle.getId())
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .tripPurpose(purpose)
                .memorySummary(memory.summary())
                .startingBatteryPercent(round(request.getCurrentBatteryPercent()))
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(totalDistanceKm)
                .baseRouteDistanceKm(baseDistanceKm)
                .chargingDetourDistanceKm(detourDistanceKm)
                .estimatedDriveMinutes(driveMinutes)
                .baseDriveMinutes(baseDriveMinutes)
                .chargingDetourMinutes(detourMinutes)
                .estimatedChargingMinutes(optimized.chargingMinutes())
                .estimatedQueueMinutes(optimized.queueMinutes())
                .connectionOverheadMinutes(optimized.connectionMinutes())
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(optimized.cost())
                .estimatedArrivalBatteryPercent(optimized.arrivalBatteryPercent())
                .feasibleAlternativesCompared(optimized.feasibleAlternatives())
                .optimizationSummary(summary)
                .routeEngine(routeEngineLabel(plan))
                .build();
        List<AutopilotStop> stops = buildOptimizedStops(proposal, plan);
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        int accumulatedStopMinutes = 0;
        double cumulativeDriveSeconds = 0;
        List<AutopilotPlanStopResponse> plannedStops = new ArrayList<>();
        for (int index = 0; index < stops.size(); index++) {
            AutopilotStop stop = stops.get(index);
            Candidate candidate = plan.selected().get(index);
            cumulativeDriveSeconds += plan.finalRoute().legs().get(index).duration();
            int arrivalOffset = (int) Math.ceil(cumulativeDriveSeconds / 60.0) + accumulatedStopMinutes;
            int stopMinutes = stop.getChargingMinutes() + stop.getEstimatedWaitMinutes()
                    + stop.getConnectionMinutes();
            int departOffset = arrivalOffset + stopMinutes;
            plannedStops.add(AutopilotPlanStopResponse.builder()
                    .sequenceNumber(stop.getSequenceNumber())
                    .stationId(stop.getStationId())
                    .stationName(stop.getStationName())
                    .stationAddress(stop.getStationAddress())
                    .connectorType(stop.getConnectorType())
                    .powerKw(stop.getPowerKw())
                    .effectivePowerKw(stop.getEffectivePowerKw())
                    .distanceFromOriginKm(stop.getDistanceFromOriginKm())
                    .routeOffsetKm(stop.getRouteOffsetKm())
                    .estimatedArrivalTime(departure.plusMinutes(arrivalOffset).format(clock))
                    .predictedSlotFreeAt(departure.plusMinutes(departOffset).format(clock))
                    .timingScore(timingScore(stop.getEstimatedWaitMinutes()))
                    .timingLabel(timingLabel(stop.getEstimatedWaitMinutes()))
                    .arrivalBatteryPercent(stop.getArrivalBatteryPercent())
                    .targetBatteryPercent(stop.getTargetBatteryPercent())
                    .estimatedWaitMinutes(stop.getEstimatedWaitMinutes())
                    .chargingMinutes(stop.getChargingMinutes())
                    .connectionMinutes(stop.getConnectionMinutes())
                    .estimatedCost(stop.getEstimatedCost())
                    .demoData(stop.isDemoData())
                    .availableConnectors((int) candidate.station().getConnectors().stream().filter(ChargingConnector::isAvailable).count())
                    .queueCount(candidate.station().getQueueCount())
                    .rating(candidate.station().getRating())
                    .selectionReason(stop.getSelectionReason())
                    .build());
            accumulatedStopMinutes += stopMinutes;
        }

        return AutopilotPlanResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleName(vehicle.getMakeAndModel())
                .registrationNumber(vehicle.getRegistrationNumber())
                .connectorType(vehicle.getConnectorType())
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .arrivalDeadline(blankToNull(request.getArrivalDeadline()))
                .estimatedArrivalTime(departure.plusMinutes(totalMinutes).format(clock))
                .optimizeFor(optimization)
                .tripPurpose(purpose.name())
                .purposeSummary(purposeSummary(purpose, request.getDestination().trim()))
                .pastExperiencesUsed(memory.totalExperiences())
                .memorySummary(memory.summary())
                .autonomyMode(autonomyMode)
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(totalDistanceKm)
                .baseRouteDistanceKm(baseDistanceKm)
                .chargingDetourDistanceKm(detourDistanceKm)
                .estimatedDriveMinutes(driveMinutes)
                .baseDriveMinutes(baseDriveMinutes)
                .chargingDetourMinutes(detourMinutes)
                .estimatedChargingMinutes(optimized.chargingMinutes())
                .estimatedQueueMinutes(optimized.queueMinutes())
                .connectionOverheadMinutes(optimized.connectionMinutes())
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(optimized.cost())
                .budgetRemaining(roundMoney(request.getMaximumChargingBudget() - optimized.cost()))
                .estimatedArrivalBatteryPercent(optimized.arrivalBatteryPercent())
                .batteryCapacityKwh(round(capacityKwh))
                .availableEnergyKwh(round(capacityKwh
                        * (request.getCurrentBatteryPercent() - request.getMinimumArrivalBatteryPercent()) / 100.0))
                .energyConsumptionKwhPer100Km(round(vehicleEnergyPerKmKwh(vehicle) * 100))
                .vehicleMaxChargingPowerKw(round(chargingProfile.maximumDcPowerKw()))
                .chargingEfficiencyPercent(round(chargingProfile.efficiency() * 100))
                .dbBoundCandidatesEvaluated(plan.dbBoundCandidates())
                .postCorridorCandidatesEvaluated(plan.postCorridorCandidates())
                .compatibleChargersEvaluated(plan.candidates().size())
                .feasibleAlternativesCompared(optimized.feasibleAlternatives())
                .optimizationSummary(summary)
                .routeEngine(routeEngineLabel(plan))
                .withinBudget(withinBudget)
                .safeArrivalReserve(safeArrivalReserve)
                .deadlineFeasible(deadlineAssessment.feasible())
                .overallFeasible(overallFeasible)
                .deadlineMinutesLate(deadlineAssessment.minutesLate())
                .liveAvailabilityChecked(true)
                .confirmationRequired(!"RECOMMEND_ONLY".equals(autonomyMode))
                .stops(plannedStops)
                .build();
    }

    @Transactional
    public AutopilotTripResponse launchTrip(Long userId, AutopilotTripRequest request) {
        String idempotencyKey = normalizedIdempotencyKey(request.getIdempotencyKey());
        var existing = tripRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Vehicle vehicle = ownedVehicle(request.getVehicleId(), userId);
        validateConstraints(request);
        LocalTime departure = LocalTime.now().withSecond(0).withNano(0);
        DeadlineEvaluator.DeadlineAssessment deadlineWindow = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, 0);
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        String autonomyMode = normalizedAutonomyMode(request.getAutonomyMode());
        if ("RECOMMEND_ONLY".equals(autonomyMode)) {
            throw new BadRequestException("Recommend Only mode cannot create bookings. Choose Ask Before Actions or Full Autopilot to launch.");
        }

        Coordinate origin = locationResolver.resolve(request.getOrigin());
        Coordinate destination = locationResolver.resolve(request.getDestination());
        double capacityKwh = batteryCapacity(vehicle);
        String optimization = normalizedOptimization(request.getOptimizeFor());
        TripPurpose purpose = resolvedPurpose(request);
        RouteMemory memory = routeMemory(request.getOrigin(), request.getDestination());
        PlanningContext plan = planJourney(
                vehicle, request, origin, destination, capacityKwh,
                optimization, purpose, memory, chargingProfile,
                deadlineWindow.availableMinutes());
        ChargingRouteOptimizer.OptimizationResult optimized = plan.optimized();
        double baseDistanceKm = round(plan.baseRoute().route().distance() / 1000.0);
        double totalDistanceKm = round(plan.finalRoute().distance() / 1000.0);
        double detourDistanceKm = round(Math.max(0, totalDistanceKm - baseDistanceKm));
        int baseDriveMinutes = (int) Math.ceil(plan.baseRoute().route().duration() / 60.0);
        int driveMinutes = (int) Math.ceil(plan.finalRoute().duration() / 60.0);
        int detourMinutes = Math.max(0, driveMinutes - baseDriveMinutes);
        int totalMinutes = driveMinutes + optimized.chargingMinutes()
                + optimized.queueMinutes() + optimized.connectionMinutes();
        DeadlineEvaluator.DeadlineAssessment deadlineAssessment = deadlineEvaluator.assess(
                request.getArrivalDeadline(), departure, totalMinutes);
        String summary = optimizationSummary(
                optimization, optimized, plan.candidates().size(), plan.corridorKm(), plan);

        double totalCost = optimized.cost();
        if (totalCost > request.getMaximumChargingBudget()) {
            throw new BadRequestException("No safe route fits the ₹" + roundMoney(request.getMaximumChargingBudget())
                    + " charging budget. Increase the budget to at least ₹" + Math.ceil(totalCost) + ".");
        }
        if (optimized.arrivalBatteryPercent() < request.getMinimumArrivalBatteryPercent()) {
            throw new BadRequestException("No route can protect the requested arrival battery reserve");
        }
        if (!deadlineAssessment.feasible()) {
            DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
            throw new BadRequestException("This plan arrives at "
                    + deadlineAssessment.estimatedArrivalTime().format(clock)
                    + ", after the requested "
                    + deadlineAssessment.requestedArrivalTime().format(clock)
                    + " deadline by " + deadlineAssessment.minutesLate()
                    + " minutes. Change the deadline before launching Autopilot.");
        }

        // Clean up any prior active trips for this user before starting a new journey
        List<AutopilotTrip> priorActiveTrips = tripRepository.findByUserIdAndStatusIn(userId, List.of(
                AutopilotTripStatus.RESERVED,
                AutopilotTripStatus.MONITORING,
                AutopilotTripStatus.REROUTED,
                AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED,
                AutopilotTripStatus.REPLAN_REQUIRED,
                AutopilotTripStatus.PAYMENT_REQUIRED
        ));
        for (AutopilotTrip prior : priorActiveTrips) {
            prior.setStatus(AutopilotTripStatus.CANCELLED);
            prior.setActiveStationId(null);
            prior.setActiveBookingId(null);
            prior.setUpdatedAt(LocalDateTime.now());
            tripRepository.save(prior);
            List<AutopilotStop> priorStops = stopRepository.findByTripIdOrderBySequenceNumberAsc(prior.getId());
            for (AutopilotStop s : priorStops) {
                if (s.getStatus() != AutopilotStopStatus.COMPLETED) {
                    if (s.getBookingId() != null) {
                        try { bookingService.cancelBooking(s.getBookingId()); } catch (Exception ignored) {}
                    }
                    s.setStatus(AutopilotStopStatus.CANCELLED);
                    stopRepository.save(s);
                }
            }
        }

        AutopilotTrip trip = tripRepository.save(AutopilotTrip.builder()
                .userId(userId)
                .vehicleId(vehicle.getId())
                .idempotencyKey(idempotencyKey)
                .goal(normalizedGoal(request))
                .origin(request.getOrigin().trim())
                .destination(request.getDestination().trim())
                .tripPurpose(purpose)
                .memorySummary(memory.summary())
                .arrivalDeadline(blankToNull(request.getArrivalDeadline()))
                .optimizeFor(optimization)
                .autonomyMode(autonomyMode)
                .startingBatteryPercent(round(request.getCurrentBatteryPercent()))
                .currentBatteryPercent(round(request.getCurrentBatteryPercent()))
                .minimumArrivalBatteryPercent(round(request.getMinimumArrivalBatteryPercent()))
                .maximumChargingBudget(roundMoney(request.getMaximumChargingBudget()))
                .totalDistanceKm(totalDistanceKm)
                .baseRouteDistanceKm(baseDistanceKm)
                .chargingDetourDistanceKm(detourDistanceKm)
                .estimatedDriveMinutes(driveMinutes)
                .baseDriveMinutes(baseDriveMinutes)
                .chargingDetourMinutes(detourMinutes)
                .estimatedChargingMinutes(optimized.chargingMinutes())
                .estimatedQueueMinutes(optimized.queueMinutes())
                .connectionOverheadMinutes(optimized.connectionMinutes())
                .totalDurationMinutes(totalMinutes)
                .estimatedChargingCost(optimized.cost())
                .estimatedArrivalBatteryPercent(optimized.arrivalBatteryPercent())
                .feasibleAlternativesCompared(optimized.feasibleAlternatives())
                .optimizationSummary(summary)
                .routeEngine(routeEngineLabel(plan))
                .status(AutopilotTripStatus.RESERVED)
                .build());

        List<AutopilotStop> stops = buildOptimizedStops(trip, plan);
        positions.setNavigation(trip, plan.finalRoute());
        if (trip.getArrivalDeadline()!=null && !trip.getArrivalDeadline().isBlank()) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deadline = now.toLocalDate().atTime(LocalTime.parse(trip.getArrivalDeadline()));
            trip.setArrivalDeadlineAt(deadline.isBefore(now) ? deadline.plusDays(1) : deadline);
        }
        if (demoDataEnabled && AutopilotPositionService.demoVehicle(vehicle)
                && plan.finalRouteEngine() != OsrmClient.RouteEngine.ESTIMATED) {
            positions.initializeDemo(trip, origin);
        }
        stopRepository.saveAll(stops);

        int chargingMinutes = optimized.chargingMinutes() + optimized.queueMinutes()
                + optimized.connectionMinutes();
        trip.setEstimatedChargingCost(roundMoney(totalCost));
        trip.setTotalDurationMinutes(trip.getEstimatedDriveMinutes() + chargingMinutes);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        addAction(trip, AutopilotActionState.SUCCESS, "Vehicle connected",
                vehicle.getMakeAndModel() + " · " + round(request.getCurrentBatteryPercent())
                        + "% battery · " + vehicle.getConnectorType());
        addAction(trip, AutopilotActionState.SUCCESS, "Goal understood",
                "Arrival by " + displayDeadline(trip.getArrivalDeadline()) + ", reserve "
                        + round(trip.getMinimumArrivalBatteryPercent()) + "%, budget ₹"
                        + roundMoney(trip.getMaximumChargingBudget()) + ".");
        addAction(trip, AutopilotActionState.INFO, "Autonomy permissions set",
                "FULL_AUTOPILOT".equals(autonomyMode)
                        ? "Automatic booking and rerouting enabled inside the approved reserve and budget limits."
                        : "The approved trip may proceed; new consequential actions remain protected by user limits.");
        addAction(trip, AutopilotActionState.SUCCESS, "Route analyzed",
                plan.candidates().size() + " chargers inside the " + corridorLimitKm(optimization)
                        + " km base-route corridor were scored; "
                        + optimized.feasibleAlternatives() + " feasible energy states reached the destination.");
        addAction(trip, AutopilotActionState.INFO, "Journey purpose applied",
                purposeSummary(purpose, trip.getDestination()));
        if (memory.totalExperiences() > 0) {
            addAction(trip, AutopilotActionState.INFO, "Past route experience applied", memory.summary());
        }
        addAction(trip, AutopilotActionState.INFO, "Trip plan created",
                stops.size() + " safe charging stop" + (stops.size() == 1 ? "" : "s")
                        + " · estimated charging ₹" + roundMoney(totalCost) + ".");

        reserveAllStops(trip, stops, userId);
        notificationService.sendNotification(userId, "Vidyut Autopilot is ready",
                "All " + stops.size() + " timing-matched charging stop(s) are tentatively reserved for "
                        + trip.getDestination() + ".",
                NotificationType.BOOKING_CONFIRMED, "vidyut://autopilot?tripId=" + trip.getId());
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public AutopilotTripResponse getCurrentTrip(Long userId) {
        return tripRepository.findFirstByUserIdAndStatusInAndUpdatedAtAfterOrderByCreatedAtDesc(userId, List.of(
                        AutopilotTripStatus.RESERVED,
                        AutopilotTripStatus.MONITORING,
                        AutopilotTripStatus.REROUTED,
                        AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED,
                        AutopilotTripStatus.REPLAN_REQUIRED,
                        AutopilotTripStatus.PAYMENT_REQUIRED
                ), LocalDateTime.now().minusHours(Math.max(1, currentTripMaxAgeHours)))
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public AutopilotTripResponse getTrip(Long tripId, Long userId) {
        return toResponse(ownedTrip(tripId, userId));
    }

    @Transactional
    public AutopilotTripResponse startJourney(Long tripId, Long userId, AutopilotProgressRequest request) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED) return toResponse(trip);

        double drop = request == null || request.getBatteryDropPercent() <= 0
                ? 6
                : request.getBatteryDropPercent();
        Vehicle vehicle = ownedVehicle(trip.getVehicleId(), userId);
        if (demoDataEnabled && AutopilotPositionService.demoVehicle(vehicle)) {
            if (!"DEMO_ROUTE_PROGRESS".equals(trip.getPositionSource())) {
                throw new BadRequestException("Start a new demo journey with a verified road route before simulating progress");
            }
            AutopilotStop next = stopRepository.findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED).orElse(null);
            if (next!=null) drop = Math.min(drop, Math.max(0, next.getDistanceFromOriginKm()-trip.getDistanceTravelledKm())
                    * vehicleEnergyPerKmKwh(vehicle)/batteryCapacity(vehicle)*100);
            positions.advanceDemo(trip, drop, batteryCapacity(vehicle), vehicleEnergyPerKmKwh(vehicle));
        }
        if (trip.getStatus()!=AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED) trip.setStatus(AutopilotTripStatus.MONITORING);
        trip.setPaymentMessage(null);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Navigation started",
                ("DEMO_ROUTE_PROGRESS".equals(trip.getPositionSource()) ? "Simulated road progress" : "Journey monitoring active") + " · battery " + round(trip.getCurrentBatteryPercent())
                        + "% · next reservation protected.");
        addAction(trip, AutopilotActionState.INFO, "Journey monitored",
                "Vidyut is watching charger status, queue changes, battery safety and budget.");
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse simulateChargerFault(Long tripId, Long userId) {
        return simulateChargerFault(tripId, userId, null);
    }

    @Transactional
    public AutopilotTripResponse simulateChargerFault(Long tripId, Long userId, Long stopId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED) {
            throw new BadRequestException("A completed trip cannot be rerouted");
        }

        AutopilotStop current = (stopId != null)
                ? ownedStop(tripId, stopId)
                : stopRepository.findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                        .orElseThrow(() -> new BadRequestException("This trip has no active charger reservation"));

        boolean executeAutomatically = automaticFaultRecoveryAllowed(trip.getAutonomyMode());
        return recoverUnavailableStop(trip, current,
                "The charger stopped responding.", executeAutomatically);
    }

    static boolean automaticFaultRecoveryAllowed(String autonomyMode) {
        return autonomyMode != null
                && "FULL_AUTOPILOT".equals(autonomyMode.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public AutopilotTripResponse recordOperationalPropagation(Long tripId, Long userId,
            String incidentCode, Long maintenanceTicketId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        String maintenance = maintenanceTicketId == null
                ? "operator follow-up requested"
                : "Company maintenance ticket #" + maintenanceTicketId;
        addAction(trip, AutopilotActionState.INFO, "Operations incident propagated",
                incidentCode + " · " + maintenance
                        + " · Host and Company notified · Admin audit recorded.");
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse endJourney(Long tripId, Long userId, Map<String, Object> request) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED || trip.getStatus() == AutopilotTripStatus.CANCELLED) {
            return toResponse(trip);
        }

        boolean explicitComplete = request != null && (
                Boolean.TRUE.equals(request.get("completed")) ||
                "COMPLETED".equalsIgnoreCase(String.valueOf(request.get("status"))) ||
                Boolean.TRUE.equals(request.get("reachedDestination"))
        );

        List<AutopilotStop> stops = stopRepository.findByTripIdOrderBySequenceNumberAsc(trip.getId());
        boolean allStopsDone = !stops.isEmpty() && stops.stream()
                .allMatch(s -> s.getStatus() == AutopilotStopStatus.COMPLETED);

        boolean reached = explicitComplete || allStopsDone;
        AutopilotTripStatus finalStatus = reached ? AutopilotTripStatus.COMPLETED : AutopilotTripStatus.CANCELLED;

        // Release future reservations and cancel incomplete stops
        for (AutopilotStop stop : stops) {
            if (stop.getStatus() != AutopilotStopStatus.COMPLETED) {
                if (stop.getBookingId() != null) {
                    try {
                        bookingService.cancelBooking(stop.getBookingId());
                    } catch (Exception ignored) {
                        // ignore if already cancelled
                    }
                }
                stop.setStatus(AutopilotStopStatus.CANCELLED);
                stopRepository.save(stop);
            }
        }

        trip.setActiveStationId(null);
        trip.setActiveBookingId(null);
        trip.setStatus(finalStatus);
        trip.setPaymentMessage(null);
        trip.setUpdatedAt(LocalDateTime.now());

        // Cancel any other lingering active trips for this user so state is completely clean
        List<AutopilotTrip> otherActiveTrips = tripRepository.findByUserIdAndStatusIn(userId, List.of(
                AutopilotTripStatus.RESERVED,
                AutopilotTripStatus.MONITORING,
                AutopilotTripStatus.REROUTED,
                AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED,
                AutopilotTripStatus.REPLAN_REQUIRED,
                AutopilotTripStatus.PAYMENT_REQUIRED
        ));
        for (AutopilotTrip other : otherActiveTrips) {
            if (!other.getId().equals(trip.getId())) {
                other.setStatus(AutopilotTripStatus.CANCELLED);
                other.setActiveStationId(null);
                other.setActiveBookingId(null);
                other.setUpdatedAt(LocalDateTime.now());
                tripRepository.save(other);
                List<AutopilotStop> otherStops = stopRepository.findByTripIdOrderBySequenceNumberAsc(other.getId());
                for (AutopilotStop s : otherStops) {
                    if (s.getStatus() != AutopilotStopStatus.COMPLETED) {
                        if (s.getBookingId() != null) {
                            try {
                                bookingService.cancelBooking(s.getBookingId());
                            } catch (Exception ignored) {}
                        }
                        s.setStatus(AutopilotStopStatus.CANCELLED);
                        stopRepository.save(s);
                    }
                }
            }
        }

        if (reached) {
            addAction(trip, AutopilotActionState.SUCCESS, "Journey completed",
                    "Arrived at " + trip.getDestination() + ". Charging reservations settled and completed.");
            notificationService.sendNotification(userId, "Journey completed",
                    "You have arrived at " + trip.getDestination() + ". Trip marked as completed.",
                    NotificationType.SYSTEM_ALERT);
        } else {
            addAction(trip, AutopilotActionState.INFO, "Journey ended by user",
                    "Active navigation ended early. Remaining charging reservations were released.");
            notificationService.sendNotification(userId, "Journey ended",
                    "Your journey to " + trip.getDestination() + " was ended and future reservations were released.",
                    NotificationType.SYSTEM_ALERT);
        }

        tripRepository.save(trip);
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse simulateArrival(Long tripId, Long userId) {
        AutopilotTrip trip = lockedTrip(tripId,userId);
        Vehicle vehicle = ownedVehicle(trip.getVehicleId(),userId);
        if (!demoDataEnabled || !AutopilotPositionService.demoVehicle(vehicle) || !"DEMO_ROUTE_PROGRESS".equals(trip.getPositionSource()))
            throw new BadRequestException("Arrival simulation is available only for a tracked demo vehicle");
        if (stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId).stream().anyMatch(s -> s.getStatus()==AutopilotStopStatus.RESERVED || s.getStatus()==AutopilotStopStatus.PLANNED))
            throw new BadRequestException("Complete the required charging stops before simulating arrival");
        double remaining = Math.max(0,positions.navigation(trip).distance()/1000-(trip.getDistanceTravelledKm()-trip.getRouteStartDistanceKm()));
        double drop = remaining*vehicleEnergyPerKmKwh(vehicle)/batteryCapacity(vehicle)*100;
        SafeRecoveryPlanner.requireReserve(trip.getCurrentBatteryPercent()-drop,trip.getMinimumArrivalBatteryPercent());
        positions.advanceDemo(trip,drop,batteryCapacity(vehicle),vehicleEnergyPerKmKwh(vehicle));
        Map<String, Object> req = new HashMap<>();
        req.put("completed", true);
        return endJourney(tripId, userId, req);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> connectorDisruptionImpact(
            Long stationId,
            String connectorType,
            Long excludedConnectorId
    ) {
        boolean backupAvailable = stationRepository.findById(stationId)
                .map(station -> station.getConnectors().stream().anyMatch(connector ->
                        !connector.getId().equals(excludedConnectorId)
                                && connector.getType().name().equalsIgnoreCase(connectorType)
                                && connector.isAvailable()
                                && !connector.isMaintenanceMode()
                                && connector.getStatus() == ChargerStatus.ONLINE))
                .orElse(false);
        List<AutopilotStop> affectedStops = stopRepository.findByStationIdAndStatus(stationId, AutopilotStopStatus.RESERVED).stream()
                        .filter(stop -> stop.getConnectorType().equalsIgnoreCase(connectorType))
                        .filter(stop -> stop.getConnectorId() != null
                                ? stop.getConnectorId().equals(excludedConnectorId) : !backupAvailable)
                        .filter(stop -> tripRepository.findById(stop.getTripId())
                                .map(trip -> activeTripStatuses().contains(trip.getStatus()))
                                .orElse(false))
                        .toList();
        long fullAutopilot = 0;
        long approvalRequired = affectedStops.stream().map(AutopilotStop::getTripId).distinct().count();
        return Map.of(
                "activeJourneys", affectedStops.stream().map(AutopilotStop::getTripId).distinct().count(),
                "automaticReroutes", fullAutopilot,
                "driverApprovals", approvalRequired,
                "backupConnectorAvailable", backupAvailable
        );
    }

    @Transactional
    public Map<String, Object> handleConnectorUnavailable(
            Long stationId,
            String connectorType,
            Long excludedConnectorId,
            String reason
    ) {
        Map<String, Object> impact = connectorDisruptionImpact(stationId, connectorType, excludedConnectorId);
        if (((Number) impact.get("activeJourneys")).longValue() == 0) {
            return Map.of("affectedJourneys", 0, "automaticReroutes", 0, "driverApprovals", 0,
                    "replanRequired", 0, "backupConnectorAvailable", impact.get("backupConnectorAvailable"));
        }

        int notified = 0;
        int automatic = 0;
        int approvals = 0;
        int replanRequired = 0;
        Set<Long> processedTrips = new HashSet<>();
        for (AutopilotStop stop : stopRepository
                .findByStationIdAndStatus(stationId, AutopilotStopStatus.RESERVED)) {
            if (!stop.getConnectorType().equalsIgnoreCase(connectorType)
                    || (stop.getConnectorId() != null ? !stop.getConnectorId().equals(excludedConnectorId)
                        : Boolean.TRUE.equals(impact.get("backupConnectorAvailable")))
                    || !processedTrips.add(stop.getTripId())) continue;
            AutopilotTrip trip = tripRepository.findById(stop.getTripId()).orElse(null);
            if (trip == null || !activeTripStatuses().contains(trip.getStatus())) continue;
            recoverUnavailableStop(trip, stop, reason, false);
            notified++;
        }
        return Map.of(
                "affectedJourneys", notified,
                "agentIncidentsPending", notified,
                "automaticReroutes", automatic,
                "driverApprovals", approvals,
                "replanRequired", replanRequired,
                "backupConnectorAvailable", impact.get("backupConnectorAvailable")
        );
    }

    @Transactional
    public AutopilotTripResponse approvePreparedReroute(Long tripId, Long userId, String incidentId, String planId) {
        return executeRecovery(tripId, userId, incidentId, planId, true);
    }

    /** Hardware events record evidence only. The authenticated EV Agent session
     * receives this incident and performs candidate lookup and preparation. */
    private AutopilotTripResponse recoverUnavailableStop(AutopilotTrip trip, AutopilotStop failed,
                                                        String reason, boolean ignoredAutomaticFlag) {
        RecoverySession previous = recoveryStore.read(trip);
        if (previous != null && previous.getFailedStopId().equals(failed.getId())
                && !"EXECUTED".equals(previous.getEvidence().getState())) return toResponse(trip);
        String incidentId = UUID.randomUUID().toString();
        var evidence = com.vidyut.autopilot.dto.AutopilotRecoveryResponse.builder()
                .incidentId(incidentId).state("INCIDENT_DETECTED").reason(reason)
                .autonomyMode(normalizedAutonomyMode(trip.getAutonomyMode()))
                .currentSoc(trip.getCurrentBatteryPercent()).reserveSoc(trip.getMinimumArrivalBatteryPercent())
                .failedStationId(failed.getStationId()).failedConnectorId(failed.getConnectorId()).build();
        recoveryStore.write(trip, RecoverySession.builder().incidentId(incidentId).failedStopId(failed.getId())
                .originalTripStatus(trip.getStatus()).evidence(evidence).build());
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.WARNING, "Charger fault detected",
                failed.getStationName() + " · connector " + failed.getConnectorId()
                        + ". Incident queued for the EV Agent. Existing reservations are unchanged.");
        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> recoveryContext(Long tripId, Long userId, String incidentId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        RecoverySession session = requireRecovery(trip, incidentId);
        return Map.of("incidentId", session.getIncidentId(), "journey", toResponse(trip));
    }

    @Transactional
    public Map<String, Object> safeRecoveryCandidates(Long tripId, Long userId, String incidentId) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        RecoverySession session = requireRecovery(trip, incidentId);
        if ("CANDIDATES_READY".equals(session.getEvidence().getState()))
            return Map.of("state", "CANDIDATES_READY", "evidence", session.getEvidence(), "candidates",
                    session.getPlans().stream().map(p -> Map.of("planId", p.id(), "plan", recoveryEvidence(session,p))).toList());
        if (!Set.of("INCIDENT_DETECTED", "NO_SAFE_RECOVERY_ROUTE", "CANDIDATES_READY").contains(session.getEvidence().getState()))
            return Map.of("state", session.getEvidence().getState(), "candidates", List.of());
        Vehicle vehicle = ownedVehicle(trip.getVehicleId(), userId);
        try {
            var evidence = recoveryPlanner.snapshot(trip, vehicle, ownedStop(tripId, session.getFailedStopId()));
            evidence.setIncidentId(incidentId);
            evidence.setAutonomyMode(normalizedAutonomyMode(trip.getAutonomyMode()));
            session.setEvidence(evidence);
            session.setPlans(recoveryPlanner.options(trip, vehicle, ownedStop(tripId, session.getFailedStopId()),
                    stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId), evidence));
            evidence.setState(session.getPlans().isEmpty() ? "NO_SAFE_RECOVERY_ROUTE" : "CANDIDATES_READY");
            evidence.setReason(session.getPlans().isEmpty()
                    ? "No verified complete recovery route meets the current battery reserve, connector, budget and deadline constraints."
                    : "Backend verified every leg of these complete remaining-journey options.");
        } catch (BadRequestException | OsrmException unavailable) {
            session.setPlans(List.of());
            session.getEvidence().setState("NO_SAFE_RECOVERY_ROUTE");
            session.getEvidence().setReason(unavailable.getMessage());
        }
        recoveryStore.write(trip, session);
        tripRepository.save(trip);
        addAction(trip, session.getPlans().isEmpty() ? AutopilotActionState.WARNING : AutopilotActionState.INFO,
                "Vidyut evaluated recovery chargers", session.getEvidence().getReason());
        return Map.of("state", session.getEvidence().getState(), "evidence", session.getEvidence(),
                "candidates", session.getPlans().stream().map(p -> Map.of("planId", p.id(), "plan", recoveryEvidence(session, p))).toList());
    }

    @Transactional
    public AutopilotTripResponse prepareSafeReroute(Long tripId, Long userId, String incidentId, String planId, String provider) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        RecoverySession session = requireRecovery(trip, incidentId);
        if (Set.of("PREPARED", "AWAITING_APPROVAL", "SUGGESTED", "EXECUTED").contains(session.getEvidence().getState())) {
            if (!java.util.Objects.equals(planId, session.getSelectedPlanId())) throw new BadRequestException("A different recovery plan is already prepared");
            return toResponse(trip);
        }
        RecoveryPlan plan = session.getPlans().stream().filter(p -> p.id().equals(planId)).findFirst()
                .orElseThrow(() -> new BadRequestException("Select a backend-issued safe recovery candidate"));
        plan = recoveryPlanner.revalidate(trip, ownedVehicle(trip.getVehicleId(), userId), ownedStop(tripId, session.getFailedStopId()),
                session.getEvidence(), plan, stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId));
        session.setPlans(List.of(plan));
        session.setSelectedPlanId(plan.id());
        session.setAgentProvider("GEMINI".equals(provider) ? provider : "AGENT_POLICY");
        String mode = normalizedAutonomyMode(trip.getAutonomyMode());
        session.getEvidence().setState("FULL_AUTOPILOT".equals(mode) ? "PREPARED"
                : "RECOMMEND_ONLY".equals(mode) ? "SUGGESTED" : "AWAITING_APPROVAL");
        if ("AWAITING_APPROVAL".equals(session.getEvidence().getState())) trip.setStatus(AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED);
        recoveryStore.write(trip, session);
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Safe recovery plan selected",
                plan.strategy() + ". Complete remaining journey validated. Selection source: " + session.getAgentProvider() + ".");
        addAction(trip, AutopilotActionState.INFO, "Remaining journey re-optimized",
                plan.stops().size() + " charging stops prepared; reservations and navigation remain unchanged.");
        if (!"FULL_AUTOPILOT".equals(mode)) addAction(trip, AutopilotActionState.INFO,
                "RECOMMEND_ONLY".equals(mode) ? "Recommendation only" : "Execution permission required",
                "RECOMMEND_ONLY".equals(mode) ? "The suggestion will not be applied in this autonomy mode." : "Driver approval is required before any reservation or route changes.");
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse executeAgentReroute(Long tripId, Long userId, String incidentId, String planId) {
        return executeRecovery(tripId, userId, incidentId, planId, false);
    }

    private AutopilotTripResponse executeRecovery(Long tripId, Long userId, String incidentId, String planId, boolean driverApproval) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        RecoverySession session = requireRecovery(trip, incidentId);
        if (!java.util.Objects.equals(planId, session.getSelectedPlanId())) throw new BadRequestException("The approved recovery proposal has changed");
        if ("EXECUTED".equals(session.getEvidence().getState())) return toResponse(trip);
        String mode = normalizedAutonomyMode(trip.getAutonomyMode());
        if ("RECOMMEND_ONLY".equals(mode) || (!driverApproval && !"FULL_AUTOPILOT".equals(mode)))
            throw new com.vidyut.common.exception.ForbiddenException("This autonomy mode does not permit automatic reroute execution");
        if (!Set.of("PREPARED", "AWAITING_APPROVAL").contains(session.getEvidence().getState()))
            throw new BadRequestException("No prepared recovery route is available");
        RecoveryPlan selected = session.getPlans().stream().filter(p -> p.id().equals(planId)).findFirst().orElseThrow();
        // Lock the exact connectors before checking them and creating bookings.
        selected.stops().stream().map(AutopilotStop::getConnectorId).distinct().sorted().forEach(id -> {
            ChargingConnector c = recoveryConnectors.findByIdForUpdate(id).orElseThrow(() -> new BadRequestException("Recovery connector disappeared"));
            if (!c.isAvailable() || c.isMaintenanceMode() || c.getStatus() != ChargerStatus.ONLINE)
                throw new BadRequestException("Recovery connector is no longer available; evaluate recovery again");
        });
        List<AutopilotStop> existing = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId);
        RecoveryPlan plan = recoveryPlanner.revalidate(trip, ownedVehicle(trip.getVehicleId(), userId), ownedStop(tripId, session.getFailedStopId()),
                session.getEvidence(), selected, existing);
        // A materially changed quote must be reviewed again, including in autopilot.
        if (Math.abs(plan.cost()-selected.cost()) > 0.01 || Math.abs(plan.distanceKm()-selected.distanceKm()) > 0.1
                || plan.totalMinutes()!=selected.totalMinutes()) throw new BadRequestException("RECOVERY_STATE_CHANGED: road or charging quote changed; evaluate recovery again");
        if (driverApproval) addAction(trip, AutopilotActionState.SUCCESS, "Driver approved reroute", "Approved proposal " + planId + ".");
        for (AutopilotStop old : existing) {
            if (old.getStatus()!=AutopilotStopStatus.RESERVED && old.getStatus()!=AutopilotStopStatus.PLANNED) continue;
            if (old.getBookingId()!=null) bookingService.cancelBookingWithoutFee(old.getBookingId(), userId, "Approved safe recovery replaced this stop.");
            old.setStatus(AutopilotStopStatus.CANCELLED);
            old.setRemovalReason(old.getId().equals(session.getFailedStopId()) ? "CHARGER_FAULT" : "ROUTE_REOPTIMIZED");
            old.setOriginalStopIndex(old.getSequenceNumber());
            stopRepository.save(old);
        }
        int sequence = existing.stream().mapToInt(AutopilotStop::getSequenceNumber).max().orElse(0);
        double arrivalSeconds = 0;
        trip.setActiveStationId(null); trip.setActiveBookingId(null);
        for (int i=0; i<plan.stops().size(); i++) {
            AutopilotStop stop = plan.stops().get(i);
            stop.setSequenceNumber(++sequence);
            stop = stopRepository.save(stop);
            arrivalSeconds += plan.route().legs().get(i).duration();
            reserveStop(trip, stop, userId, (int)Math.ceil(arrivalSeconds/60), i==0);
            arrivalSeconds += 60.0*(stop.getChargingMinutes()+stop.getEstimatedWaitMinutes()+stop.getConnectionMinutes());
        }
        positions.setNavigation(trip, plan.route());
        trip.setTotalDistanceKm(trip.getDistanceTravelledKm()+plan.distanceKm());
        trip.setEstimatedDriveMinutes(trip.getElapsedDriveMinutes()+plan.driveMinutes());
        int completedCharge = existing.stream().filter(s->s.getStatus()==AutopilotStopStatus.COMPLETED).mapToInt(AutopilotStop::getChargingMinutes).sum();
        double completedCost = existing.stream().filter(s->s.getStatus()==AutopilotStopStatus.COMPLETED).mapToDouble(AutopilotStop::getEstimatedCost).sum();
        trip.setEstimatedChargingMinutes(completedCharge+plan.chargingMinutes());
        trip.setEstimatedQueueMinutes(plan.queueMinutes()); trip.setConnectionOverheadMinutes(plan.connectionMinutes());
        trip.setTotalDurationMinutes(trip.getElapsedDriveMinutes()+completedCharge+plan.totalMinutes());
        trip.setEstimatedChargingCost(completedCost+plan.cost()); trip.setEstimatedArrivalBatteryPercent(plan.destinationArrivalSoc());
        trip.setRouteEngine(plan.engine().name()); trip.setStatus(AutopilotTripStatus.REROUTED);
        trip.setUpdatedAt(LocalDateTime.now());
        session.setPlans(List.of(plan)); session.getEvidence().setState("EXECUTED");
        session.getEvidence().setCapturedAt(AutopilotPositionService.now());
        recoveryStore.write(trip, session); tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Navigation updated", driverApproval
                ? "Approved recovery route applied and all recovery stops reserved."
                : "Vidyut automatically rerouted your journey within the configured reserve, budget and deadline constraints.");
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse updatePosition(Long tripId, Long userId, com.vidyut.autopilot.dto.AutopilotPositionRequest request) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        positions.recordGps(trip, request);
        tripRepository.save(trip);
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse refreshRecovery(Long tripId, Long userId, String incidentId) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        RecoverySession session = requireRecovery(trip, incidentId);
        if ("EXECUTED".equals(session.getEvidence().getState())) throw new BadRequestException("Recovery has already executed");
        session.setSelectedPlanId(null); session.setPlans(List.of()); session.getEvidence().setState("INCIDENT_DETECTED");
        trip.setStatus(session.getOriginalTripStatus());
        recoveryStore.write(trip, session); tripRepository.save(trip);
        return toResponse(trip);
    }

    private AutopilotTrip lockedTrip(Long tripId, Long userId) {
        return tripRepository.findOwnedForUpdate(tripId, userId).orElseThrow(() -> new ResourceNotFoundException("Autopilot trip not found for this account"));
    }

    private RecoverySession requireRecovery(AutopilotTrip trip, String incidentId) {
        RecoverySession session = recoveryStore.read(trip);
        if (session==null || !session.getIncidentId().equals(incidentId) || !activeTripStatuses().contains(trip.getStatus()))
            throw new BadRequestException("No matching active recovery incident");
        return session;
    }

    private com.vidyut.autopilot.dto.AutopilotRecoveryResponse recoveryView(AutopilotTrip trip) {
        RecoverySession session = recoveryStore.read(trip);
        if (session==null) return null;
        return session.getPlans().stream().filter(p -> p.id().equals(session.getSelectedPlanId())).findFirst()
                .map(p -> recoveryEvidence(session,p)).orElse(session.getEvidence());
    }

    private com.vidyut.autopilot.dto.AutopilotRecoveryResponse recoveryEvidence(RecoverySession session, RecoveryPlan plan) {
        var e = session.getEvidence().toBuilder().build();
        e.setPlanId(plan.id()); e.setStrategy(plan.strategy()); e.setAgentProvider(session.getAgentProvider());
        e.setProposedStops(plan.stops().stream().map(this::mapStop).toList());
        if (!plan.stops().isEmpty()) {
            AutopilotStop bridge = plan.stops().get(0);
            e.setBridgeConnectorId(bridge.getConnectorId()); e.setPredictedArrivalSoc(bridge.getArrivalBatteryPercent());
            e.setDepartureTargetSoc(bridge.getTargetBatteryPercent());
        }
        e.setDistanceToBridgeKm(plan.route().legs().get(0).distance()/1000); e.setEnergyToBridgeKwh(plan.firstLegEnergyKwh());
        e.setOriginalRemainingDistanceKm(plan.originalRemainingDistanceKm()); e.setOriginalRemainingMinutes(plan.originalRemainingMinutes());
        e.setNewRemainingDistanceKm(plan.distanceKm()); e.setNewRemainingMinutes(plan.totalMinutes());
        e.setAdditionalDistanceKm(plan.originalRemainingDistanceKm()==null ? null : plan.distanceKm()-plan.originalRemainingDistanceKm());
        e.setAdditionalMinutes(plan.originalRemainingMinutes()==null ? null : plan.totalMinutes()-plan.originalRemainingMinutes());
        e.setAdditionalCost(plan.cost()-plan.originalRemainingCost()); e.setRemainingCost(plan.cost());
        e.setEstimatedArrivalTime(session.getEvidence().getCapturedAt().plusMinutes(plan.totalMinutes())); e.setRouteEngine(plan.engine().name());
        return e;
    }


    private Set<AutopilotTripStatus> activeTripStatuses() {
        return Set.of(
                AutopilotTripStatus.RESERVED,
                AutopilotTripStatus.MONITORING,
                AutopilotTripStatus.REROUTED,
                AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED,
                AutopilotTripStatus.REPLAN_REQUIRED,
                AutopilotTripStatus.PAYMENT_REQUIRED
        );
    }

    @Transactional
    public AutopilotTripResponse completeCharging(Long tripId, Long userId) {
        AutopilotTrip trip = lockedTrip(tripId, userId);
        if (trip.getStatus() == AutopilotTripStatus.COMPLETED) return toResponse(trip);

        AutopilotStop activeStop = stopRepository
                .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElseThrow(() -> new BadRequestException("This trip has no active charging reservation"));

        RecoverySession recovery = recoveryStore.read(trip);
        if (recovery!=null && recovery.getFailedStopId().equals(activeStop.getId()) && !"EXECUTED".equals(recovery.getEvidence().getState()))
            throw new BadRequestException("The failed connector cannot complete charging. Resolve the recovery incident first.");

        Vehicle vehicle = ownedVehicle(trip.getVehicleId(), userId);
        ChargingStation chargingSite = stationRepository.findById(activeStop.getStationId()).orElseThrow(() -> new BadRequestException("Charging station no longer exists"));
        Coordinate sitePoint = new Coordinate(chargingSite.getLatitude(),chargingSite.getLongitude());
        boolean demo = demoDataEnabled && AutopilotPositionService.demoVehicle(vehicle) && "DEMO_ROUTE_PROGRESS".equals(trip.getPositionSource());
        if (demo) {
            double remainingKm = Math.max(0,activeStop.getDistanceFromOriginKm()-trip.getDistanceTravelledKm());
            double drop = remainingKm*vehicleEnergyPerKmKwh(vehicle)/batteryCapacity(vehicle)*100;
            SafeRecoveryPlanner.requireReserve(trip.getCurrentBatteryPercent()-drop,trip.getMinimumArrivalBatteryPercent());
            positions.advanceDemo(trip,drop,batteryCapacity(vehicle),vehicleEnergyPerKmKwh(vehicle));
            trip.setCurrentLatitude(sitePoint.latitude()); trip.setCurrentLongitude(sitePoint.longitude());
            trip.setDistanceTravelledKm(activeStop.getDistanceFromOriginKm()); trip.setPositionRecordedAt(AutopilotPositionService.now());
        } else if (RecoveryRoadService.distanceKm(positions.current(trip),sitePoint)>0.5) {
            throw new BadRequestException("Fresh vehicle telemetry at this charger is required before completing charging");
        }

        WalletResponse wallet = walletService.getWalletByUserId(userId);
        List<AutoRechargeRuleResponse> rules = walletService.getAutoRechargeRules(userId);
        boolean canAutoRecharge = rules.stream()
                .filter(rule -> rule.getVehicleId().equals(trip.getVehicleId()) && rule.isEnabled())
                .anyMatch(rule -> wallet.getBalance() + rule.getRechargeAmount() >= activeStop.getEstimatedCost());

        if (wallet.getBalance() < activeStop.getEstimatedCost() && !canAutoRecharge) {
            trip.setStatus(AutopilotTripStatus.PAYMENT_REQUIRED);
            trip.setPaymentMessage("Add ₹" + Math.ceil(activeStop.getEstimatedCost() - wallet.getBalance())
                    + " or enable vehicle auto-recharge to finish AutoPay.");
            trip.setUpdatedAt(LocalDateTime.now());
            tripRepository.save(trip);
            addAction(trip, AutopilotActionState.WARNING, "AutoPay needs attention", trip.getPaymentMessage());
            remember(trip, activeStop.getStationId(), RouteExperienceOutcome.PAYMENT_ISSUE,
                    trip.getPaymentMessage(), null, null);
            return toResponse(trip);
        }

        PaymentResponse payment = paymentService.processPayment(userId, PaymentRequest.builder()
                .bookingId(activeStop.getBookingId())
                .amount(activeStop.getEstimatedCost())
                .paymentMethod("VIDYUT_WALLET_AUTOPAY")
                .build());

        activeStop.setStatus(AutopilotStopStatus.COMPLETED);
        stopRepository.save(activeStop);
        trip.setCurrentBatteryPercent(activeStop.getTargetBatteryPercent());
        AutopilotStop nextStop = stopRepository
                .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElse(null);
        if (nextStop == null) {
            nextStop = stopRepository
                    .findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.PLANNED)
                    .orElse(null);
            if (nextStop != null) {
                // Trips created by older versions reserved only the first stop. Promote the
                // next planned stop instead of incorrectly completing the whole journey.
                reserveNextStop(trip, nextStop, userId);
            }
        }
        trip.setStatus(AutopilotTripStatus.MONITORING);
        trip.setActiveStationId(nextStop == null ? null : nextStop.getStationId());
        trip.setActiveBookingId(nextStop == null ? null : nextStop.getBookingId());
        trip.setPaymentMessage("Paid with Vidyut AutoPay · " + payment.getGatewayTransactionId());
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);

        addAction(trip, AutopilotActionState.SUCCESS, "Charging completed",
                round(activeStop.getTargetBatteryPercent()) + "% battery · connector released.");
        addAction(trip, AutopilotActionState.SUCCESS, "Wallet paid automatically",
                "₹" + roundMoney(activeStop.getEstimatedCost()) + " paid · " + payment.getGatewayTransactionId() + ".");
        addAction(trip, AutopilotActionState.SUCCESS,
                "Journey continues",
                nextStop == null ? "Charging stops are complete; navigation continues to the destination."
                        : "Navigation resumed toward " + nextStop.getStationName() + ".");
        remember(trip, activeStop.getStationId(), RouteExperienceOutcome.SUCCESS,
                "Charging completed and AutoPay succeeded at " + activeStop.getStationName() + ".", 5, 0);
        notificationService.sendNotification(userId, "Charging and AutoPay complete",
                "₹" + roundMoney(activeStop.getEstimatedCost()) + " paid at " + activeStop.getStationName() + ".",
                NotificationType.CHARGING_COMPLETED);
        return toResponse(trip);
    }

    @Transactional
    public RouteExperienceResponse recordExperience(Long tripId, Long userId, RouteExperienceRequest input) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        if (input.stationId() != null) {
            boolean belongsToTrip = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId).stream()
                    .anyMatch(stop -> stop.getStationId().equals(input.stationId()));
            if (!belongsToTrip) throw new BadRequestException("The selected station was not part of this trip");
        }
        RouteExperience saved = experienceRepository.save(RouteExperience.builder()
                .userId(userId).tripId(tripId).stationId(input.stationId())
                .origin(trip.getOrigin()).destination(trip.getDestination())
                .originKey(routeKey(trip.getOrigin())).destinationKey(routeKey(trip.getDestination()))
                .outcome(input.outcome()).detail(blankToNull(input.detail()))
                .rating(input.rating()).delayMinutes(input.delayMinutes()).build());
        addAction(trip, AutopilotActionState.INFO, "Route experience saved",
                "Future plans on this corridor will retrieve this "
                        + input.outcome().name().toLowerCase(Locale.ROOT) + " signal before selecting a stop.");
        return mapExperience(saved);
    }

    @Transactional(readOnly = true)
    public List<RouteStationResponse> stopAlternatives(Long tripId, Long stopId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        AutopilotStop stop = ownedStop(tripId, stopId);
        return routingService.alternatives(userId, stop.getStationId(), trip.getVehicleId());
    }

    @Transactional
    public AutopilotTripResponse swapStop(Long tripId, Long stopId, Long alternativeStationId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        AutopilotStop stop = ownedStop(tripId, stopId);
        RouteStationResponse alternative = routingService.alternatives(
                        userId, stop.getStationId(), trip.getVehicleId()).stream()
                .filter(candidate -> candidate.getStation().getId().equals(alternativeStationId))
                .findFirst().orElseThrow(() -> new BadRequestException(
                        "The selected station is not a compatible live alternative"));
        if (stop.getBookingId() != null) {
            bookingService.cancelBookingWithoutFee(stop.getBookingId(), userId,
                    "Autopilot stop swapped by the driver");
        }
        double oldCost = stop.getEstimatedCost();
        var station = alternative.getStation();
        var connector = station.getConnectors().stream()
                .filter(ChargingConnector::isAvailable)
                .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElseThrow(() -> new BadRequestException("Alternative has no available connector"));
        stop.setStationId(station.getId());
        stop.setStationName(station.getName());
        stop.setStationAddress(station.getAddress());
        stop.setConnectorId(connector.getId());
        stop.setChargerCode(connector.getChargerCode());
        stop.setConnectorType(connector.getType().name());
        stop.setPowerKw(connector.getPowerKw());
        stop.setDistanceFromOriginKm(alternative.getDistanceFromOriginKm());
        stop.setEstimatedWaitMinutes(Math.max(0, station.getQueueCount() * 7));
        stop.setChargingMinutes(alternative.getRecommendedChargeMinutes());
        stop.setEstimatedCost(alternative.getEstimatedChargingCost());
        stop.setSelectionReason(alternative.getReason());
        stop.setBookingId(null);
        stop.setStatus(AutopilotStopStatus.PLANNED);
        stopRepository.save(stop);
        reserveNextStop(trip, stop, userId);
        trip.setEstimatedChargingCost(roundMoney(
                Math.max(0, trip.getEstimatedChargingCost() - oldCost + stop.getEstimatedCost())));
        trip.setStatus(AutopilotTripStatus.REROUTED);
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Charging stop swapped",
                station.getName() + " is reserved. Downstream arrival estimates were recalculated.");
        notificationService.sendNotification(userId, "Trip plan updated",
                station.getName() + " replaced the previous charging stop.",
                NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + tripId);
        return toResponse(trip);
    }

    @Transactional
    public AutopilotTripResponse simulateDelay(Long tripId, Long userId, int delayMinutes) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        addAction(trip, AutopilotActionState.WARNING, "Delay detected",
                "The trip is running " + delayMinutes + " minutes behind. Timing-matched stops are being checked.");
        notificationService.sendNotification(userId, "Vidyut is replanning",
                "You are running " + delayMinutes + " minutes behind. Tap to review the updated stop.",
                NotificationType.AGENT_REPLAN, "vidyut://autopilot?tripId=" + tripId);
        return simulateChargerFault(tripId, userId);
    }

    @Transactional(readOnly = true)
    public AutopilotTripSummaryResponse summary(Long tripId, Long userId) {
        AutopilotTrip trip = ownedTrip(tripId, userId);
        List<AutopilotStop> stops = stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId);
        int chargingMinutes = stops.stream().filter(stop -> stop.getStatus() != AutopilotStopStatus.CANCELLED)
                .mapToInt(AutopilotStop::getChargingMinutes).sum();
        int stopCount = (int) stops.stream()
                .filter(stop -> stop.getStatus() != AutopilotStopStatus.CANCELLED).count();
        double co2 = round(trip.getTotalDistanceKm() * 0.12);
        String share = "Vidyut trip " + trip.getOrigin() + " → " + trip.getDestination() + ": "
                + trip.getTotalDistanceKm() + " km, " + stopCount + " charging stops, ₹"
                + trip.getEstimatedChargingCost() + ", " + co2 + " kg CO₂ saved.";
        return new AutopilotTripSummaryResponse(tripId, trip.getOrigin(), trip.getDestination(),
                trip.getTotalDistanceKm(), trip.getTotalDurationMinutes(), chargingMinutes,
                stopCount, trip.getEstimatedChargingCost(), co2, share);
    }

    private void validateConstraints(AutopilotTripRequest request) {
        if (request.getCurrentBatteryPercent() <= request.getMinimumArrivalBatteryPercent()) {
            throw new BadRequestException("Current battery must be above the requested arrival reserve");
        }
    }

    private PlanningContext planJourney(
            Vehicle vehicle,
            AutopilotTripRequest request,
            Coordinate origin,
            Coordinate destination,
            double capacityKwh,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            VehicleChargingProfileService.ChargingProfile chargingProfile,
            Integer maximumTotalMinutes
    ) {
        return planJourney(vehicle, request, origin, destination, capacityKwh, optimizeFor, purpose, memory, chargingProfile, maximumTotalMinutes, Collections.emptySet());
    }

    private PlanningContext planJourney(
            Vehicle vehicle,
            AutopilotTripRequest request,
            Coordinate origin,
            Coordinate destination,
            double capacityKwh,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            VehicleChargingProfileService.ChargingProfile chargingProfile,
            Integer maximumTotalMinutes,
            Set<Long> excludedStationIds
    ) {
        BaseRoute baseRoute = getBestBaseRoute(origin, destination);
        CandidateDiscovery discovery = routeCandidates(
                vehicle, baseRoute.route(), optimizeFor, purpose, memory, excludedStationIds);
        List<Candidate> candidates = discovery.candidates();
        List<Coordinate> matrixCoordinates = new ArrayList<>();
        matrixCoordinates.add(origin);
        candidates.stream().map(this::stationCoordinate).forEach(matrixCoordinates::add);
        matrixCoordinates.add(destination);

        OsrmClient.MatrixSelection matrixSelection =
                osrmClient.getBestFullTable(matrixCoordinates, baseRoute.engine());
        OsrmTableResponse matrix = matrixSelection.response();

        List<ChargingRouteOptimizer.ChargingOption> options = candidates.stream()
                .map(candidate -> new ChargingRouteOptimizer.ChargingOption(
                        candidate.station().getId(),
                        candidate.distanceFromOriginKm(),
                        candidate.routeOffsetKm(),
                        candidate.connector().getPowerKw(),
                        candidate.station().getPricePerKwh(),
                        candidate.waitMinutes(),
                        candidate.station().getRating()
                ))
                .toList();
        double energyPerKmKwh = vehicleEnergyPerKmKwh(vehicle);
        ChargingRouteOptimizer.OptimizationRequest optimizationRequest =
                new ChargingRouteOptimizer.OptimizationRequest(
                        options,
                        matrix,
                        capacityKwh,
                        energyPerKmKwh,
                        request.getCurrentBatteryPercent(),
                        request.getMinimumArrivalBatteryPercent(),
                        request.getMaximumChargingBudget(),
                        optimizeFor,
                        chargingProfile.maximumDcPowerKw(),
                        chargingProfile.efficiency(),
                        chargingProfile.curve(),
                        maximumTotalMinutes
                );

        ChargingRouteOptimizer.OptimizationResult optimized;
        try {
            optimized = chargingRouteOptimizer.optimize(optimizationRequest);
        } catch (BadRequestException budgetOrFeasibilityFailure) {
            try {
                optimized = chargingRouteOptimizer.optimize(
                        new ChargingRouteOptimizer.OptimizationRequest(
                                options,
                                matrix,
                                capacityKwh,
                                energyPerKmKwh,
                                request.getCurrentBatteryPercent(),
                                request.getMinimumArrivalBatteryPercent(),
                                Double.MAX_VALUE,
                                optimizeFor,
                                chargingProfile.maximumDcPowerKw(),
                                chargingProfile.efficiency(),
                                chargingProfile.curve(),
                                maximumTotalMinutes
                        ));
            } catch (BadRequestException deadlineOrEnergyFailure) {
                optimized = chargingRouteOptimizer.optimize(
                        new ChargingRouteOptimizer.OptimizationRequest(
                                options,
                                matrix,
                                capacityKwh,
                                energyPerKmKwh,
                                request.getCurrentBatteryPercent(),
                                request.getMinimumArrivalBatteryPercent(),
                                Double.MAX_VALUE,
                                optimizeFor,
                                chargingProfile.maximumDcPowerKw(),
                                chargingProfile.efficiency(),
                                chargingProfile.curve(),
                                null
                        ));
            }
        }

        Map<Long, Candidate> candidateByStation = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> candidate.station().getId(),
                        candidate -> candidate));
        List<Candidate> selected = optimized.stops().stream()
                .map(stop -> candidateByStation.get(stop.option().stationId()))
                .toList();
        if (selected.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BadRequestException("The optimizer returned an unknown charging station");
        }

        List<Coordinate> waypoints = new ArrayList<>();
        waypoints.add(origin);
        selected.stream().map(this::stationCoordinate).forEach(waypoints::add);
        waypoints.add(destination);
        BaseRoute finalRouteSelection = getBestRoute(waypoints);
        OsrmRoute finalRoute = finalRouteSelection.route();
        if (finalRoute.legs() == null || finalRoute.legs().size() != selected.size() + 1) {
            throw new BadRequestException("The route engine did not return every optimized journey leg");
        }

        return new PlanningContext(
                baseRoute,
                candidates,
                selected,
                optimized,
                finalRoute,
                finalRouteSelection.engine(),
                matrixSelection,
                discovery.dbBoundCandidates(),
                discovery.postCorridorCandidates(),
                discovery.corridorKm());
    }

    private CandidateDiscovery routeCandidates(
            Vehicle vehicle,
            OsrmRoute baseRoute,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory
    ) {
        return routeCandidates(vehicle, baseRoute, optimizeFor, purpose, memory, Collections.emptySet());
    }

    private CandidateDiscovery routeCandidates(
            Vehicle vehicle,
            OsrmRoute baseRoute,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            Set<Long> excludedStationIds
    ) {
        boolean isReplan = excludedStationIds != null && !excludedStationIds.isEmpty();
        boolean isEstimated = baseRoute.geometry() != null && baseRoute.geometry().coordinates() != null && baseRoute.geometry().coordinates().size() <= 2;
        double corridorKm = isReplan ? Math.max(corridorLimitKm(optimizeFor) * 5.0, 260.0)
                : isEstimated ? Math.max(corridorLimitKm(optimizeFor) * 8.0, 260.0)
                : corridorLimitKm(optimizeFor);
        double baseDistanceKm = baseRoute.distance() / 1000.0;
        CandidateEvaluation evaluation = evaluateCandidates(
                vehicle, baseRoute, corridorKm, baseDistanceKm, optimizeFor, purpose, memory, excludedStationIds);
        if (corridorKm < 260.0 && (evaluation.candidates().size() < 4
                || needsChargingCoverageExpansion(vehicle, evaluation.candidates(), baseDistanceKm))) {
            corridorKm = Math.max(corridorKm * 3.5, 260.0);
            evaluation = evaluateCandidates(
                    vehicle, baseRoute, corridorKm, baseDistanceKm, optimizeFor, purpose, memory, excludedStationIds);
        }
        List<Candidate> optimizerCandidates = optimizerCandidates(
                evaluation.candidates(), baseDistanceKm);
        return new CandidateDiscovery(
                optimizerCandidates,
                evaluation.dbBoundCandidates(),
                evaluation.candidates().size(),
                corridorKm);
    }

    private List<Candidate> optimizerCandidates(List<Candidate> candidates, double baseDistanceKm) {
        Comparator<Candidate> routeOrder = Comparator.comparingDouble(Candidate::distanceFromOriginKm);
        if (candidates.size() <= 60) {
            return candidates.stream().sorted(routeOrder).toList();
        }

        int binCount = 20;
        Map<Integer, List<Candidate>> byProgressBin = candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(candidate -> Math.min(
                        binCount - 1,
                        Math.max(0, (int) Math.floor(
                                candidate.distanceFromOriginKm() / Math.max(1, baseDistanceKm) * binCount)))));
        Comparator<Candidate> binPreference = Comparator
                .comparingInt((Candidate candidate) -> isDistrictHub(candidate.station()) ? 1 : 0)
                .thenComparingDouble(Candidate::routeOffsetKm)
                .thenComparingDouble(Candidate::impactMinutes)
                .thenComparing(Comparator.comparingDouble(
                        (Candidate candidate) -> candidate.connector().getPowerKw()).reversed());

        Map<Long, Candidate> selected = new LinkedHashMap<>();
        candidates.stream()
                .filter(this::isCanonicalDemoHub)
                .sorted(routeOrder)
                .forEach(candidate -> selected.putIfAbsent(candidate.station().getId(), candidate));

        Map<Integer, List<Candidate>> rankedBins = new TreeMap<>();
        byProgressBin.forEach((bin, values) -> rankedBins.put(
                bin, values.stream().sorted(binPreference).toList()));
        for (int rank = 0; selected.size() < 60; rank++) {
            boolean foundCandidateAtRank = false;
            for (List<Candidate> bin : rankedBins.values()) {
                if (rank >= bin.size()) continue;
                foundCandidateAtRank = true;
                Candidate candidate = bin.get(rank);
                selected.putIfAbsent(candidate.station().getId(), candidate);
                if (selected.size() >= 60) break;
            }
            if (!foundCandidateAtRank) break;
        }
        return selected.values().stream().sorted(routeOrder).toList();
    }

    private boolean isCanonicalDemoHub(Candidate candidate) {
        String seedKey = candidate.station().getDemoSeedKey();
        return seedKey != null && seedKey.endsWith("_DEMO_01");
    }

    private boolean isDistrictHub(ChargingStation station) {
        return station.getDemoSeedKey() != null && station.getDemoSeedKey().startsWith("SOI-");
    }

    private boolean needsChargingCoverageExpansion(
            Vehicle vehicle,
            List<Candidate> candidates,
            double baseDistanceKm
    ) {
        if (candidates.isEmpty()) {
            return true;
        }
        double conservativeChargedRangeKm = batteryCapacity(vehicle) * 0.75
                / vehicleEnergyPerKmKwh(vehicle);
        double maximumProgressGapKm = 0;
        double previousProgressKm = 0;
        for (Candidate candidate : candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceFromOriginKm))
                .toList()) {
            maximumProgressGapKm = Math.max(
                    maximumProgressGapKm,
                    candidate.distanceFromOriginKm() - previousProgressKm);
            previousProgressKm = candidate.distanceFromOriginKm();
        }
        maximumProgressGapKm = Math.max(maximumProgressGapKm, baseDistanceKm - previousProgressKm);

        // Route progress is shorter than the true road distance between off-centre
        // stations. Expand before optimization when the coarse corridor has less
        // than a 15% reachability margin for a charged leg.
        return maximumProgressGapKm > conservativeChargedRangeKm * 0.85;
    }

    private CandidateEvaluation evaluateCandidates(
            Vehicle vehicle,
            OsrmRoute baseRoute,
            double corridorKm,
            double baseDistanceKm,
            String optimizeFor,
            TripPurpose purpose,
            RouteMemory memory,
            Set<Long> excludedStationIds
    ) {
        RouteBounds routeBounds = routeBounds(baseRoute.geometry(), corridorKm);
        List<Candidate> candidates = new ArrayList<>();
        List<ChargingStation> boundedStations = stationRepository.findPublishedStationsWithinBounds(
                routeBounds.minimumLatitude(), routeBounds.maximumLatitude(),
                routeBounds.minimumLongitude(), routeBounds.maximumLongitude(),
                demoDataEnabled);

        for (ChargingStation station : boundedStations) {
            if (excludedStationIds != null && excludedStationIds.contains(station.getId())) {
                continue;
            }
            if (station.getStatus() != StationStatus.ACTIVE
                    || station.getAvailability() == StationAvailability.UNAVAILABLE
                    || station.isEmergencyDisabled()) {
                continue;
            }
            if (!routeBounds.contains(station.getLatitude(), station.getLongitude())) {
                continue;
            }
            ChargingConnector connector = bestConnector(station, vehicle);
            if (connector == null) {
                continue;
            }

            RouteCorridorService.RouteMatch routeMatch;
            try {
                routeMatch = routeCorridorService.match(
                        new Coordinate(station.getLatitude(), station.getLongitude()),
                        baseRoute.geometry());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            boolean isReplan = excludedStationIds != null && !excludedStationIds.isEmpty();
            double endpointBufferKm = isReplan ? 2.0 : Math.min(Math.max(0, stopEndpointBufferKm), Math.max(3.0, baseDistanceKm * 0.04));
            if (routeMatch.offsetKm() > corridorKm
                    || routeMatch.progressKm() <= endpointBufferKm
                    || routeMatch.progressKm() >= baseDistanceKm - endpointBufferKm) {
                continue;
            }

            int waitMinutes = Math.max(0, station.getQueueCount() * 7)
                    + (int) Math.round(station.getOccupancyPercent() / 20.0 * 3);
            MemorySignal signal = memory.stationSignals().getOrDefault(station.getId(), MemorySignal.EMPTY);
            String amenities = station.getAmenities() == null ? "" : station.getAmenities().toLowerCase(Locale.ROOT);
            boolean restFriendly = amenities.matches(".*(restroom|restaurant|food|cafe|lounge|hotel|washroom).*");
            double reliabilityPenalty = Math.max(0, 5 - station.getRating()) * 8
                    + signal.failures() * 55 + signal.averageDelayMinutes() * 0.7;
            double impact = waitMinutes + reliabilityPenalty
                    + station.getPricePerKwh() * ("COST".equals(optimizeFor) ? 2 : 0.25);
            String reason = round(routeMatch.offsetKm()) + " km from the base route; "
                    + station.getAvailableSlots() + " live compatible connector(s)";
            if (purpose == TripPurpose.REST_STOP && restFriendly) {
                reason += "; rest amenities available";
            }
            candidates.add(new Candidate(
                    station,
                    connector,
                    round(routeMatch.progressKm()),
                    round(Math.max(0, baseDistanceKm - routeMatch.progressKm())),
                    round(routeMatch.offsetKm() * 2),
                    round(routeMatch.offsetKm()),
                    waitMinutes,
                    impact,
                    restFriendly,
                    reason
            ));
        }

        return new CandidateEvaluation(candidates, boundedStations.size());
    }

    private RouteBounds routeBounds(OsrmGeometry geometry, double corridorKm) {
        if (geometry == null || geometry.coordinates() == null || geometry.coordinates().isEmpty()) {
            return RouteBounds.INDIA;
        }
        double minimumLatitude = Double.POSITIVE_INFINITY;
        double maximumLatitude = Double.NEGATIVE_INFINITY;
        double minimumLongitude = Double.POSITIVE_INFINITY;
        double maximumLongitude = Double.NEGATIVE_INFINITY;
        for (List<Double> coordinate : geometry.coordinates()) {
            if (coordinate == null || coordinate.size() < 2) continue;
            minimumLongitude = Math.min(minimumLongitude, coordinate.get(0));
            maximumLongitude = Math.max(maximumLongitude, coordinate.get(0));
            minimumLatitude = Math.min(minimumLatitude, coordinate.get(1));
            maximumLatitude = Math.max(maximumLatitude, coordinate.get(1));
        }
        if (!Double.isFinite(minimumLatitude) || !Double.isFinite(minimumLongitude)) {
            return RouteBounds.INDIA;
        }
        // Coarse rejection only: the exact polyline distance check below still owns
        // corridor eligibility. The generous conversion protects curved routes.
        double marginDegrees = Math.max(0.2, corridorKm / 80.0);
        return new RouteBounds(
                minimumLatitude - marginDegrees,
                maximumLatitude + marginDegrees,
                minimumLongitude - marginDegrees,
                maximumLongitude + marginDegrees);
    }

    private List<AutopilotStop> buildOptimizedStops(AutopilotTrip trip, PlanningContext plan) {
        List<AutopilotStop> stops = new ArrayList<>();
        double cumulativeDistanceKm = 0;
        for (int index = 0; index < plan.selected().size(); index++) {
            Candidate candidate = plan.selected().get(index);
            ChargingRouteOptimizer.StopDecision decision = plan.optimized().stops().get(index);
            cumulativeDistanceKm += plan.finalRoute().legs().get(index).distance() / 1000.0;
            stops.add(AutopilotStop.builder()
                    .tripId(trip.getId())
                    .sequenceNumber(index + 1)
                    .stationId(candidate.station().getId())
                    .stationName(candidate.station().getName())
                    .stationAddress(candidate.station().getAddress())
                    .connectorId(candidate.connector().getId())
                    .chargerCode(candidate.connector().getChargerCode())
                    .connectorType(candidate.connector().getType().name())
                    .powerKw(round(candidate.connector().getPowerKw()))
                    .effectivePowerKw(decision.effectivePowerKw())
                    .distanceFromOriginKm(round(cumulativeDistanceKm))
                    .routeOffsetKm(finalRouteOffsetKm(candidate, plan.finalRoute()))
                    .arrivalBatteryPercent(decision.arrivalBatteryPercent())
                    .targetBatteryPercent(decision.targetBatteryPercent())
                    .estimatedWaitMinutes(decision.queueMinutes())
                    .chargingMinutes(decision.chargingMinutes())
                    .connectionMinutes(decision.connectionMinutes())
                    .estimatedCost(decision.cost())
                    .demoData(candidate.station().isDemoData())
                    .selectionReason(candidate.selectionReason())
                    .status(AutopilotStopStatus.PLANNED)
                    .build());
        }
        return stops;
    }

    private double finalRouteOffsetKm(Candidate candidate, OsrmRoute finalRoute) {
        try {
            return round(routeCorridorService.match(stationCoordinate(candidate), finalRoute.geometry()).offsetKm());
        } catch (IllegalArgumentException ignored) {
            // The selected station is an explicit final-route waypoint. If an
            // estimated routing fallback has no usable geometry, report zero
            // rather than leaking its distance from the superseded base route.
            return 0;
        }
    }

    private double corridorLimitKm(String optimizeFor) {
        return switch (normalizedOptimization(optimizeFor)) {
            case "COST" -> Math.max(25, costCorridorKm);
            case "BALANCED" -> Math.max(18, balancedCorridorKm);
            default -> Math.max(14, timeCorridorKm);
        };
    }

    private String optimizationSummary(
            String optimizeFor,
            ChargingRouteOptimizer.OptimizationResult optimized,
            int candidateCount,
            double corridorKm,
            PlanningContext plan
    ) {
        String objective = switch (normalizedOptimization(optimizeFor)) {
            case "COST" -> "lowest-cost";
            case "BALANCED" -> "best time-cost-reliability";
            default -> "fastest";
        };
        String summary = "Selected the " + objective + " battery-safe plan after evaluating "
                + optimized.transitionsEvaluated() + " energy transitions across "
                + candidateCount + " chargers within " + round(corridorKm)
                + " km of the base route. Stops are retained only when they improve the objective "
                + "or are required to protect the battery reserve.";
        if (plan.baseRoute().engine() == OsrmClient.RouteEngine.ESTIMATED) {
            return summary + " Live road routing was unavailable, so distance and timing use a "
                    + "conservative estimated-road fallback.";
        }
        if (plan.matrixSelection().estimatedCells()
                || plan.finalRouteEngine() == OsrmClient.RouteEngine.ESTIMATED) {
            return summary + " Some charger-leg road data was unavailable and was replaced with "
                    + "conservative estimates; confirm navigation before departure.";
        }
        return summary;
    }

    private String routeEngineLabel(PlanningContext plan) {
        String base = switch (plan.baseRoute().engine()) {
            case GOOGLE -> "GOOGLE_ROUTES_TRAFFIC_AWARE";
            case REFERENCE -> "OSRM_FULL_MAP_REFERENCE";
            case ESTIMATED -> "ESTIMATED_ROAD_FALLBACK";
            default -> "OSRM_LOCAL_OPENSTREETMAP";
        };
        if (plan.baseRoute().engine() == OsrmClient.RouteEngine.ESTIMATED) {
            return base;
        }
        if (plan.matrixSelection().estimatedCells()
                || plan.finalRouteEngine() == OsrmClient.RouteEngine.ESTIMATED) {
            return base + "_WITH_ESTIMATED_CHARGER_LEGS";
        }
        return base;
    }

    private Double matrixValue(List<List<Double>> matrix, int row, int column) {
        if (matrix == null || row < 0 || row >= matrix.size()) {
            return null;
        }
        List<Double> values = matrix.get(row);
        return values == null || column < 0 || column >= values.size() ? null : values.get(column);
    }

    private Itinerary safeItinerary(
            Coordinate origin,
            List<Candidate> candidates,
            List<Candidate> initiallySelected,
            Coordinate destination,
            OsrmRoute directRoute,
            double capacityKwh,
            double startingBattery,
            double minimumBattery
    ) {
        List<Candidate> selected = new ArrayList<>(initiallySelected);
        double firstLegRangeKm = capacityKwh * Math.max(0, startingBattery - minimumBattery)
                / 100.0 / ENERGY_PER_KM_KWH;
        double chargedLegRangeKm = capacityKwh * Math.max(0, 80 - minimumBattery)
                / 100.0 / ENERGY_PER_KM_KWH;
        double directDistanceKm = directRoute.distance() / 1000.0;

        while (true) {
            Itinerary itinerary = itinerary(origin, selected, destination, directRoute);
            int unsafeLeg = -1;
            for (int legIndex = 0; legIndex < itinerary.legDistancesKm().size(); legIndex++) {
                double availableRangeKm = legIndex == 0 ? firstLegRangeKm : chargedLegRangeKm;
                if (itinerary.legDistancesKm().get(legIndex) > availableRangeKm) {
                    unsafeLeg = legIndex;
                    break;
                }
            }
            if (unsafeLeg < 0) {
                return itinerary;
            }
            if (selected.size() >= MAX_STOPS) {
                throw new BadRequestException("A safe route needs more than " + MAX_STOPS + " charging stops");
            }

            Candidate bridge = bridgeCandidate(
                    origin, destination, candidates, selected, unsafeLeg,
                    unsafeLeg == 0 ? firstLegRangeKm : chargedLegRangeKm,
                    directDistanceKm
            );
            if (bridge == null) {
                throw new BadRequestException(
                        "No compatible charger can safely bridge a "
                                + round(itinerary.legDistancesKm().get(unsafeLeg)) + " km route leg");
            }
            selected.add(unsafeLeg, bridge);
        }
    }

    private Candidate bridgeCandidate(
            Coordinate origin,
            Coordinate destination,
            List<Candidate> candidates,
            List<Candidate> selected,
            int legIndex,
            double availableRangeKm,
            double directDistanceKm
    ) {
        Set<Long> selectedStationIds = selected.stream()
                .map(candidate -> candidate.station().getId())
                .collect(java.util.stream.Collectors.toSet());
        double previousProgressKm = legIndex == 0
                ? 0
                : selected.get(legIndex - 1).distanceFromOriginKm();
        double nextProgressKm = legIndex < selected.size()
                ? selected.get(legIndex).distanceFromOriginKm()
                : directDistanceKm;
        List<Candidate> possible = candidates.stream()
                .filter(candidate -> !selectedStationIds.contains(candidate.station().getId()))
                .filter(candidate -> candidate.distanceFromOriginKm() > previousProgressKm + 1)
                .filter(candidate -> candidate.distanceFromOriginKm() < nextProgressKm - 1)
                .toList();
        if (possible.isEmpty()) {
            return null;
        }

        Coordinate previous = legIndex == 0
                ? origin
                : stationCoordinate(selected.get(legIndex - 1));
        Coordinate next = legIndex < selected.size()
                ? stationCoordinate(selected.get(legIndex))
                : destination;
        List<Coordinate> possibleCoordinates = possible.stream()
                .map(this::stationCoordinate)
                .toList();
        Map<Integer, double[]> metrics = new HashMap<>();
        for (OsrmClient.MatrixBatch batch : osrmClient.getBestMatrixTables(
                previous, possibleCoordinates, next, OsrmClient.RouteEngine.PRIMARY)) {
            OsrmTableResponse table = batch.response();
            if (table == null || !"Ok".equals(table.code()) || table.distances() == null) {
                continue;
            }
            int batchStationCount = batch.stationCoordinates().size();
            for (int localIndex = 0; localIndex < batchStationCount; localIndex++) {
                Double fromPreviousM = matrixValue(table.distances(), 0, localIndex);
                Double toNextM = matrixValue(table.distances(), localIndex + 1, batchStationCount);
                if (fromPreviousM != null && toNextM != null) {
                    metrics.put(batch.stationIndexes().get(localIndex),
                            new double[]{fromPreviousM / 1000.0, toNextM / 1000.0});
                }
            }
        }

        Candidate best = null;
        double bestScore = Double.MAX_VALUE;
        for (int index = 0; index < possible.size(); index++) {
            double[] metric = metrics.get(index);
            if (metric == null || metric[0] > availableRangeKm) {
                continue;
            }
            Candidate candidate = possible.get(index);
            double score = metric[1] + candidate.impactMinutes() * 0.05;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private Coordinate stationCoordinate(Candidate candidate) {
        return new Coordinate(candidate.station().getLatitude(), candidate.station().getLongitude());
    }

    private Itinerary itinerary(
            Coordinate origin,
            List<Candidate> selected,
            Coordinate destination,
            OsrmRoute directRoute
    ) {
        if (selected.isEmpty()) {
            return new Itinerary(
                    round(directRoute.distance() / 1000.0),
                    (int) Math.ceil(directRoute.duration() / 60.0),
                    selected,
                    List.of(round(directRoute.distance() / 1000.0))
            );
        }

        List<Coordinate> waypoints = new ArrayList<>();
        waypoints.add(origin);
        selected.stream()
                .map(candidate -> new Coordinate(
                        candidate.station().getLatitude(),
                        candidate.station().getLongitude()))
                .forEach(waypoints::add);
        waypoints.add(destination);

        OsrmRoute waypointRoute = getOsrmRoute(waypoints);
        if (waypointRoute.legs() == null || waypointRoute.legs().size() != selected.size() + 1) {
            throw new BadRequestException("The routing engine did not return every charging-stop route leg");
        }

        double totalDistanceKm = waypointRoute.distance() / 1000.0;
        double cumulativeDistanceKm = 0;
        List<Candidate> adjusted = new ArrayList<>();
        List<Double> legDistancesKm = waypointRoute.legs().stream()
                .map(leg -> round(leg.distance() / 1000.0))
                .toList();
        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            cumulativeDistanceKm += waypointRoute.legs().get(index).distance() / 1000.0;
            adjusted.add(new Candidate(
                    candidate.station(),
                    candidate.connector(),
                    round(cumulativeDistanceKm),
                    round(Math.max(0, totalDistanceKm - cumulativeDistanceKm)),
                    candidate.detourKm(),
                    candidate.routeOffsetKm(),
                    candidate.waitMinutes(),
                    candidate.impactMinutes(),
                    candidate.restFriendly(),
                    candidate.selectionReason()
            ));
        }

        return new Itinerary(
                round(totalDistanceKm),
                (int) Math.ceil(waypointRoute.duration() / 60.0),
                List.copyOf(adjusted),
                legDistancesKm
        );
    }

    private List<AutopilotStop> buildStops(AutopilotTrip trip, List<Candidate> selected, double capacityKwh) {
        List<AutopilotStop> stops = new ArrayList<>();
        double previousMarker = 0;
        double previousBattery = trip.getStartingBatteryPercent();

        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            double arrivalBattery = Math.max(
                    trip.getMinimumArrivalBatteryPercent() + 1,
                    previousBattery - (candidate.distanceFromOriginKm() - previousMarker)
                            * ENERGY_PER_KM_KWH / capacityKwh * 100
            );
            double nextMarker = index + 1 < selected.size()
                    ? selected.get(index + 1).distanceFromOriginKm()
                    : trip.getTotalDistanceKm();
            double batteryNeeded = (nextMarker - candidate.distanceFromOriginKm())
                    * ENERGY_PER_KM_KWH / capacityKwh * 100;

            double targetBattery;
            if (index + 1 >= selected.size()) {
                targetBattery = Math.min(80, Math.max(arrivalBattery + 10, trip.getMinimumArrivalBatteryPercent() + 5 + batteryNeeded));
            } else {
                targetBattery = Math.min(80, Math.max(arrivalBattery + 20, Math.min(80, trip.getMinimumArrivalBatteryPercent() + 10 + batteryNeeded)));
                targetBattery = Math.max(targetBattery, Math.min(80, arrivalBattery + 30));
            }

            double energyAdded = capacityKwh * (targetBattery - arrivalBattery) / 100.0;
            int chargingMinutes = Math.max(6,
                    (int) Math.ceil(energyAdded / Math.max(7.4, candidate.connector().getPowerKw()) * 60) + 3);

            stops.add(AutopilotStop.builder()
                    .tripId(trip.getId())
                    .sequenceNumber(index + 1)
                    .stationId(candidate.station().getId())
                    .stationName(candidate.station().getName())
                    .stationAddress(candidate.station().getAddress())
                    .connectorId(candidate.connector().getId())
                    .chargerCode(candidate.connector().getChargerCode())
                    .connectorType(candidate.connector().getType().name())
                    .powerKw(round(candidate.connector().getPowerKw()))
                    .distanceFromOriginKm(candidate.distanceFromOriginKm())
                    .arrivalBatteryPercent(round(arrivalBattery))
                    .targetBatteryPercent(round(targetBattery))
                    .estimatedWaitMinutes(candidate.waitMinutes())
                    .chargingMinutes(chargingMinutes)
                    .estimatedCost(roundMoney(energyAdded * candidate.station().getPricePerKwh()))
                    .demoData(candidate.station().isDemoData())
                    .selectionReason(candidate.selectionReason())
                    .status(AutopilotStopStatus.PLANNED)
                    .build());
            previousMarker = candidate.distanceFromOriginKm();
            previousBattery = targetBattery;
        }
        return stops;
    }

    private AutopilotStop createReplacementStop(
            AutopilotTrip trip,
            AutopilotStop replaced,
            Candidate candidate,
            Vehicle vehicle,
            double capacityKwh
    ) {
        double arrival = Math.max(
                trip.getMinimumArrivalBatteryPercent() + 1,
                replaced.getArrivalBatteryPercent());
        double target = Math.max(arrival + 10, Math.min(80, replaced.getTargetBatteryPercent()));
        double energy = capacityKwh * (target - arrival) / 100.0;
        VehicleChargingProfileService.ChargingProfile chargingProfile =
                vehicleChargingProfileService.forVehicle(vehicle);
        ChargingRouteOptimizer.ChargeEstimate chargeEstimate = chargingRouteOptimizer.estimateCharge(
                capacityKwh,
                candidate.connector().getPowerKw(),
                chargingProfile.maximumDcPowerKw(),
                chargingProfile.efficiency(),
                chargingProfile.curve(),
                arrival,
                target);
        return AutopilotStop.builder()
                .tripId(trip.getId())
                .sequenceNumber(replaced.getSequenceNumber())
                .stationId(candidate.station().getId())
                .stationName(candidate.station().getName())
                .stationAddress(candidate.station().getAddress())
                .connectorId(candidate.connector().getId())
                    .chargerCode(candidate.connector().getChargerCode())
                    .connectorType(candidate.connector().getType().name())
                .powerKw(round(candidate.connector().getPowerKw()))
                .effectivePowerKw(round(chargeEstimate.effectivePowerKw()))
                .distanceFromOriginKm(candidate.distanceFromOriginKm())
                .routeOffsetKm(candidate.routeOffsetKm())
                .arrivalBatteryPercent(round(arrival))
                .targetBatteryPercent(round(target))
                .estimatedWaitMinutes(candidate.waitMinutes())
                .chargingMinutes(chargeEstimate.minutes())
                .connectionMinutes(4)
                .estimatedCost(roundMoney(energy * candidate.station().getPricePerKwh()))
                .demoData(candidate.station().isDemoData())
                .selectionReason(candidate.selectionReason())
                .status(AutopilotStopStatus.PLANNED)
                .build();
    }

    private void reserveNextStop(AutopilotTrip trip, AutopilotStop stop, Long userId) {
        reserveStop(trip, stop, userId, Math.max(15, trip.getEstimatedDriveMinutes() / 3), true);
    }

    private void reserveAllStops(AutopilotTrip trip, List<AutopilotStop> stops, Long userId) {
        int accumulatedStopMinutes = 0;
        for (int index = 0; index < stops.size(); index++) {
            AutopilotStop stop = stops.get(index);
            int arrivalMinutes = (int) Math.ceil(stop.getDistanceFromOriginKm() / 68.0 * 60)
                    + accumulatedStopMinutes;
            reserveStop(trip, stop, userId, Math.max(15, arrivalMinutes), index == 0);
            accumulatedStopMinutes += stop.getEstimatedWaitMinutes() + stop.getChargingMinutes();
        }
        addAction(trip, AutopilotActionState.SUCCESS, "All stops tentatively booked",
                stops.size() + " reservations were created in one confirmation.");
    }

    private void reserveStop(AutopilotTrip trip, AutopilotStop stop, Long userId,
                             int arrivalMinutes, boolean makeActive) {
        BookingResponse booking = bookingService.createBooking(BookingCreateRequest.builder()
                .stationId(stop.getStationId())
                .connectorId(stop.getConnectorId())
                .vehicleId(trip.getVehicleId())
                .startTime(LocalDateTime.now().plusMinutes(arrivalMinutes))
                .durationHours(Math.max(1, (int) Math.ceil(stop.getChargingMinutes() / 60.0)))
                .idempotencyKey("AUTOPILOT-" + trip.getId() + "-STOP-" + stop.getId())
                .build(), userId);
        stop.setBookingId(booking.getId());
        stop.setStatus(AutopilotStopStatus.RESERVED);
        stopRepository.save(stop);
        if (makeActive) {
            trip.setActiveStationId(stop.getStationId());
            trip.setActiveBookingId(booking.getId());
        }
        trip.setPaymentMessage("Wallet authorization will complete after charging.");
        trip.setUpdatedAt(LocalDateTime.now());
        tripRepository.save(trip);
        addAction(trip, AutopilotActionState.SUCCESS, "Connector reserved",
                stop.getStationName() + " · " + stop.getConnectorType() + " · booking #" + booking.getId() + ".");
    }

    private ChargingConnector bestConnector(ChargingStation station, String vehicleConnector) {
        String normalized = normalizeConnector(vehicleConnector);
        return station.getConnectors().stream()
                .filter(ChargingConnector::isAvailable)
                .filter(connector -> !connector.isMaintenanceMode())
                .filter(connector -> connector.getStatus() == ChargerStatus.ONLINE)
                .filter(connector -> normalized.contains(connector.getType().name())
                        || connector.getType().name().contains(normalized))
                .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElse(null);
    }

    private ChargingConnector bestConnector(ChargingStation station, Vehicle vehicle) {
        Set<String> supported = supportedConnectorNames(vehicle);
        return station.getConnectors().stream()
                .filter(ChargingConnector::isAvailable)
                .filter(connector -> !connector.isMaintenanceMode())
                .filter(connector -> connector.getStatus() == ChargerStatus.ONLINE)
                .filter(connector -> supported.contains(connector.getType().name()))
                .max(Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElse(null);
    }

    private String normalizeConnector(String connector) {
        if (connector == null) return "CCS2";
        String normalized = connector.toUpperCase(Locale.ROOT)
                .replace("TYPE 2", "TYPE2")
                .replace("CHADEMO", "CHADEMO")
                .replaceAll("[^A-Z0-9/]", "");
        if (normalized.contains("BHARATDC001") || normalized.equals("GBT")) return "GB_T";
        return normalized.isBlank() ? "CCS2" : normalized;
    }

    private Vehicle ownedVehicle(Long vehicleId, Long userId) {
        return vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
    }

    private AutopilotTrip ownedTrip(Long tripId, Long userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Autopilot trip not found for this account"));
    }

    private void addAction(AutopilotTrip trip, AutopilotActionState state, String title, String detail) {
        int sequence = (int) actionRepository.countByTripId(trip.getId()) + 1;
        actionRepository.save(AutopilotAction.builder()
                .tripId(trip.getId())
                .sequenceNumber(sequence)
                .state(state)
                .title(title)
                .detail(detail)
                .build());
    }

    private AutopilotTripResponse toResponse(AutopilotTrip trip) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(trip.getVehicleId(), trip.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this trip"));
        double capacity = batteryCapacity(vehicle);
        WalletResponse wallet = walletService.getWalletByUserId(trip.getUserId());
        return AutopilotTripResponse.builder()
                .id(trip.getId())
                .idempotencyKey(trip.getIdempotencyKey())
                .goal(trip.getGoal())
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .arrivalDeadline(trip.getArrivalDeadline())
                .optimizeFor(trip.getOptimizeFor())
                .tripPurpose(trip.getTripPurpose() == null ? TripPurpose.GENERAL.name() : trip.getTripPurpose().name())
                .memorySummary(trip.getMemorySummary())
                .autonomyMode(normalizedAutonomyMode(trip.getAutonomyMode()))
                .minimumArrivalBatteryPercent(trip.getMinimumArrivalBatteryPercent())
                .maximumChargingBudget(trip.getMaximumChargingBudget())
                .totalDistanceKm(trip.getTotalDistanceKm())
                .baseRouteDistanceKm(trip.getBaseRouteDistanceKm())
                .chargingDetourDistanceKm(trip.getChargingDetourDistanceKm())
                .estimatedDriveMinutes(trip.getEstimatedDriveMinutes())
                .baseDriveMinutes(trip.getBaseDriveMinutes())
                .chargingDetourMinutes(trip.getChargingDetourMinutes())
                .estimatedChargingMinutes(trip.getEstimatedChargingMinutes())
                .estimatedQueueMinutes(trip.getEstimatedQueueMinutes())
                .connectionOverheadMinutes(trip.getConnectionOverheadMinutes())
                .totalDurationMinutes(trip.getTotalDurationMinutes())
                .estimatedChargingCost(trip.getEstimatedChargingCost())
                .estimatedArrivalBatteryPercent(trip.getEstimatedArrivalBatteryPercent())
                .feasibleAlternativesCompared(trip.getFeasibleAlternativesCompared())
                .optimizationSummary(trip.getOptimizationSummary())
                .routeEngine(trip.getRouteEngine())
                .activeStationId(trip.getActiveStationId())
                .activeBookingId(trip.getActiveBookingId())
                .status(trip.getStatus())
                .paymentMessage(trip.getPaymentMessage())
                .walletBalance(roundMoney(wallet.getBalance()))
                .telemetry(AutopilotTelemetryResponse.builder()
                        .vehicleId(vehicle.getId())
                        .vehicleName(vehicle.getMakeAndModel())
                        .registrationNumber(vehicle.getRegistrationNumber())
                        .connectorType(vehicle.getConnectorType())
                        .batteryCapacityKwh(capacity)
                        .batteryPercent(trip.getCurrentBatteryPercent())
                        .remainingRangeKm(round(capacity * trip.getCurrentBatteryPercent() / 100.0
                                / vehicleEnergyPerKmKwh(vehicle)))
                        .state(telemetryState(trip.getStatus()))
                        .latitude(trip.getCurrentLatitude()).longitude(trip.getCurrentLongitude())
                        .positionRecordedAt(trip.getPositionRecordedAt()).positionSource(trip.getPositionSource())
                        .distanceTravelledKm(trip.getDistanceTravelledKm())
                        .safeReachableDistanceKm(Math.max(0, trip.getCurrentBatteryPercent()-trip.getMinimumArrivalBatteryPercent())
                                /100 * capacity / vehicleEnergyPerKmKwh(vehicle))
                        .build())
                .recovery(recoveryView(trip))
                .routeCoordinates(trip.getNavigationRouteJson()==null ? null : positions.navigation(trip).geometry().coordinates())
                .stops(stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(trip.getId()).stream()
                        .map(this::mapStop)
                        .toList())
                .timeline(actionRepository.findByTripIdOrderBySequenceNumberAsc(trip.getId()).stream()
                        .map(this::mapAction)
                        .toList())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }

    private AutopilotStopResponse mapStop(AutopilotStop stop) {
        ChargingStation station = stationRepository.findById(stop.getStationId()).orElse(null);
        return AutopilotStopResponse.builder()
                .latitude(station==null ? null : station.getLatitude()).longitude(station==null ? null : station.getLongitude())
                .id(stop.getId())
                .sequenceNumber(stop.getSequenceNumber())
                .stationId(stop.getStationId())
                .bookingId(stop.getBookingId())
                .stationName(stop.getStationName())
                .stationAddress(stop.getStationAddress())
                .connectorId(stop.getConnectorId())
                .chargerCode(stop.getChargerCode())
                .connectorType(stop.getConnectorType())
                .powerKw(stop.getPowerKw())
                .effectivePowerKw(stop.getEffectivePowerKw())
                .distanceFromOriginKm(stop.getDistanceFromOriginKm())
                .routeOffsetKm(stop.getRouteOffsetKm())
                .arrivalBatteryPercent(stop.getArrivalBatteryPercent())
                .targetBatteryPercent(stop.getTargetBatteryPercent())
                .estimatedWaitMinutes(stop.getEstimatedWaitMinutes())
                .chargingMinutes(stop.getChargingMinutes())
                .connectionMinutes(stop.getConnectionMinutes())
                .estimatedCost(stop.getEstimatedCost())
                .demoData(stop.isDemoData())
                .selectionReason(stop.getSelectionReason())
                .timingScore(timingScore(stop.getEstimatedWaitMinutes()))
                .timingLabel(timingLabel(stop.getEstimatedWaitMinutes()))
                .status(stop.getStatus())
                .selectionType(stop.getSelectionType())
                .replacesStationId(stop.getReplacesStationId())
                .replacesStationName(stop.getReplacesStationName())
                .rerouteReason(stop.getRerouteReason())
                .additionalDistanceKm(stop.getAdditionalDistanceKm())
                .additionalMinutes(stop.getAdditionalMinutes())
                .additionalCost(stop.getAdditionalCost())
                .removalReason(stop.getRemovalReason())
                .replacedByStationId(stop.getReplacedByStationId())
                .replacedByStationName(stop.getReplacedByStationName())
                .originalStopIndex(stop.getOriginalStopIndex())
                .build();
    }

    private AutopilotActionResponse mapAction(AutopilotAction action) {
        return AutopilotActionResponse.builder()
                .sequenceNumber(action.getSequenceNumber())
                .state(action.getState())
                .title(action.getTitle())
                .detail(action.getDetail())
                .timestamp(action.getTimestamp())
                .build();
    }

    private VehicleRecommendationOptionResponse recommendationOption(
            Vehicle vehicle,
            double currentBattery,
            AutopilotPlanResponse plan,
            boolean feasible,
            String reason
    ) {
        List<String> connectors = supportedConnectorNames(vehicle).stream().sorted().toList();
        boolean hasDcConnector = connectors.stream()
                .anyMatch(connector -> connector.equals("CCS2")
                        || connector.equals("CHADEMO") || connector.equals("GB_T"));
        double maximumPower = hasDcConnector
                ? validPositive(vehicle.getMaxDcChargePowerKw(), 50)
                : validPositive(vehicle.getMaxAcChargePowerKw(), 7.2);
        return VehicleRecommendationOptionResponse.builder()
                .vehicleId(vehicle.getId())
                .vehicleName(vehicle.getMakeAndModel())
                .registrationNumber(vehicle.getRegistrationNumber())
                .supportedConnectors(connectors)
                .batteryCapacityKwh(round(batteryCapacity(vehicle)))
                .currentBatteryPercent(round(currentBattery))
                .efficiencyWhPerKm(round(vehicleEnergyPerKmKwh(vehicle) * 1000))
                .maximumChargingPowerKw(round(maximumPower))
                .feasible(feasible)
                .reason(reason)
                .compatibleChargersEvaluated(plan == null ? 0 : plan.getCompatibleChargersEvaluated())
                .chargingStops(plan == null || plan.getStops() == null ? 0 : plan.getStops().size())
                .journeyMinutes(plan == null ? 0 : plan.getTotalDurationMinutes())
                .chargingMinutes(plan == null ? 0 : plan.getEstimatedChargingMinutes())
                .estimatedCost(plan == null ? 0 : plan.getEstimatedChargingCost())
                .arrivalBatteryPercent(plan == null ? 0 : plan.getEstimatedArrivalBatteryPercent())
                .withinBudget(plan != null && plan.isWithinBudget())
                .deadlineFeasible(plan != null && plan.isDeadlineFeasible())
                .build();
    }

    private String feasibleVehicleReason(AutopilotPlanResponse plan, String optimization) {
        return switch (optimization) {
            case "COST" -> "Feasible with " + plan.getStops().size()
                    + " compatible charging stop(s) at an estimated ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + ".";
            case "BALANCED" -> "Feasible balance of " + formatMinutesForReason(plan.getTotalDurationMinutes())
                    + ", ₹" + roundMoney(plan.getEstimatedChargingCost()) + " and "
                    + plan.getStops().size() + " charging stop(s).";
            default -> "Feasible in " + formatMinutesForReason(plan.getTotalDurationMinutes())
                    + " with " + plan.getStops().size() + " compatible charging stop(s).";
        };
    }

    private String recommendedVehicleReason(AutopilotPlanResponse plan, String optimization) {
        return switch (optimization) {
            case "COST" -> "Lowest estimated charging cost among feasible cars: ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + " with "
                    + plan.getStops().size() + " stop(s).";
            case "BALANCED" -> "Best feasible balance of total time, charging cost and stop count: "
                    + formatMinutesForReason(plan.getTotalDurationMinutes()) + ", ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + " and "
                    + plan.getStops().size() + " stop(s).";
            default -> "Lowest estimated total journey time among feasible cars: "
                    + formatMinutesForReason(plan.getTotalDurationMinutes()) + " with "
                    + plan.getStops().size() + " stop(s).";
        };
    }

    private String infeasiblePlanReason(AutopilotPlanResponse plan) {
        if (!plan.isWithinBudget() && !plan.isDeadlineFeasible()) {
            return "A battery-safe route exists, but it exceeds the charging budget and misses the arrival deadline.";
        }
        if (!plan.isWithinBudget()) {
            return "A battery-safe route exists, but its estimated ₹"
                    + roundMoney(plan.getEstimatedChargingCost()) + " charging cost exceeds the budget.";
        }
        if (!plan.isDeadlineFeasible()) {
            return "A battery-safe route exists, but it misses the arrival deadline by "
                    + plan.getDeadlineMinutesLate() + " minutes.";
        }
        return "The route cannot maintain the requested arrival battery reserve.";
    }

    private String planningFailureReason(Vehicle vehicle, BadRequestException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        String normalized = message.toLowerCase(Locale.ROOT);
        String connectors = String.join(" + ", supportedConnectorNames(vehicle).stream().sorted().toList());
        if (normalized.contains("charger sequence")
                || normalized.contains("safely reachable")
                || normalized.contains("safely bridge")
                || normalized.contains("safe route")) {
            return "No reachable compatible " + connectors
                    + " charging chain can maintain the requested battery reserve on this road corridor.";
        }
        if (normalized.contains("no online charger") || normalized.contains("connector")) {
            return "Compatible " + connectors + " charging coverage is insufficient on this corridor.";
        }
        return message.isBlank() ? "The route could not be evaluated for this vehicle." : message;
    }

    private String formatMinutesForReason(int minutes) {
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private Set<String> supportedConnectorNames(Vehicle vehicle) {
        if (vehicle.getSupportedConnectors() != null && !vehicle.getSupportedConnectors().isEmpty()) {
            return vehicle.getSupportedConnectors().stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        }
        return Set.of(normalizeConnector(vehicle.getConnectorType()));
    }

    private double vehicleEnergyPerKmKwh(Vehicle vehicle) {
        Double efficiency = vehicle.getEfficiencyWhPerKm();
        return efficiency != null && Double.isFinite(efficiency) && efficiency >= 50 && efficiency <= 500
                ? efficiency / 1000.0
                : ENERGY_PER_KM_KWH;
    }

    private double validPositive(Double value, double fallback) {
        return value != null && Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private double batteryCapacity(Vehicle vehicle) {
        if (vehicle.getBatteryCapacity() == null) return 40.5;
        String numeric = vehicle.getBatteryCapacity().replaceAll("[^0-9.]", "");
        try {
            double parsed = Double.parseDouble(numeric);
            return parsed > 10 && parsed < 250 ? parsed : 40.5;
        } catch (NumberFormatException ignored) {
            return 40.5;
        }
    }

    private String normalizedGoal(AutopilotTripRequest request) {
        if (request.getGoal() != null && !request.getGoal().isBlank()) return request.getGoal().trim();
        return "Get me from " + request.getOrigin().trim() + " to " + request.getDestination().trim()
                + " by " + displayDeadline(request.getArrivalDeadline()) + ". Keep charging under ₹"
                + roundMoney(request.getMaximumChargingBudget()) + " and battery above "
                + round(request.getMinimumArrivalBatteryPercent()) + "%.";
    }

    private AutopilotStop ownedStop(Long tripId, Long stopId) {
        return stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(tripId).stream()
                .filter(stop -> stop.getId().equals(stopId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Autopilot stop not found for this trip"));
    }

    private String timingScore(int waitMinutes) {
        if (waitMinutes <= 15) return "HIGH";
        if (waitMinutes <= 30) return "MEDIUM";
        return "LOW";
    }

    private String timingLabel(int waitMinutes) {
        if (waitMinutes <= 15) return "Arrives just in time";
        if (waitMinutes <= 30) return "Short wait · about " + waitMinutes + " min";
        return "Better option may be nearby";
    }

    private TripPurpose resolvedPurpose(AutopilotTripRequest request) {
        if (request.getTripPurpose() != null && !request.getTripPurpose().isBlank()) {
            try {
                return TripPurpose.valueOf(request.getTripPurpose().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to natural-language intent detection.
            }
        }
        String intent = ((request.getGoal() == null ? "" : request.getGoal()) + " "
                + (request.getDestination() == null ? "" : request.getDestination())).toLowerCase(Locale.ROOT);
        if (intent.matches(".*(mall|shopping|market|cinema).*")) return TripPurpose.MALL_VISIT;
        if (intent.matches(".*(rest|break|food|meal|cafe|hotel|washroom|sleep).*")) return TripPurpose.REST_STOP;
        if (intent.matches(".*(office|work|commute|college|school).*")) return TripPurpose.COMMUTE;
        if (intent.matches(".*(charge near|charger near|destination charg).*")) return TripPurpose.DESTINATION_CHARGING;
        return TripPurpose.GENERAL;
    }

    private String purposeSummary(TripPurpose purpose, String destination) {
        return switch (purpose) {
            case MALL_VISIT -> "Mall visit: prefer a compatible charger close to " + destination
                    + " so parking time also becomes charging time.";
            case REST_STOP -> "Rest stop: prefer an on-route charger with food, restroom, lounge or hotel amenities.";
            case COMMUTE -> "Daily commute: optimize for low queue delay and repeat reliability.";
            case DESTINATION_CHARGING -> "Destination charging: arrive near " + destination
                    + " with minimal highway stops and charge at the finish.";
            case GENERAL -> "General journey: balance safe range, detour, live queue, charging speed and price.";
        };
    }

    private String telemetryState(AutopilotTripStatus status) {
        return switch (status) {
            case RESERVED -> "STANDBY";
            case MONITORING -> "DRIVING";
            case REROUTED -> "REROUTING";
            case REROUTE_APPROVAL_REQUIRED -> "WAITING_FOR_DRIVER";
            case REPLAN_REQUIRED -> "SAFE_STOP_REQUIRED";
            case PAYMENT_REQUIRED -> "CHARGING_COMPLETE";
            case COMPLETED -> "PARKED";
            case CANCELLED -> "DISCONNECTED";
        };
    }

    private String statusLabel(AutopilotTripStatus status) {
        return switch (status) {
            case RESERVED -> "CHARGER RESERVED";
            case MONITORING -> "AUTONOMOUS MONITORING";
            case REROUTED -> "AUTOMATICALLY REROUTED";
            case REROUTE_APPROVAL_REQUIRED -> "REROUTE APPROVAL NEEDED";
            case REPLAN_REQUIRED -> "SAFE REPLAN NEEDED";
            case PAYMENT_REQUIRED -> "ACTION REQUIRED";
            case COMPLETED -> "JOURNEY COMPLETED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private void remember(AutopilotTrip trip, Long stationId, RouteExperienceOutcome outcome,
                          String detail, Integer rating, Integer delayMinutes) {
        if (stationId == null) return;
        experienceRepository.save(RouteExperience.builder()
                .userId(trip.getUserId())
                .tripId(trip.getId())
                .stationId(stationId)
                .origin(trip.getOrigin())
                .destination(trip.getDestination())
                .originKey(routeKey(trip.getOrigin()))
                .destinationKey(routeKey(trip.getDestination()))
                .outcome(outcome)
                .detail(detail)
                .rating(rating)
                .delayMinutes(delayMinutes)
                .build());
    }

    private RouteMemory routeMemory(String origin, String destination) {
        List<RouteExperience> experiences = experienceRepository
                .findTop30ByOriginKeyAndDestinationKeyOrderByCreatedAtDesc(routeKey(origin), routeKey(destination));
        Map<Long, MemorySignal> signals = new HashMap<>();
        for (RouteExperience item : experiences) {
            if (item.getStationId() == null) continue;
            signals.compute(item.getStationId(), (k, v) -> {
                MemorySignal current = v == null ? MemorySignal.EMPTY : v;
                int successes = current.successes() + (item.getOutcome() == RouteExperienceOutcome.SUCCESS ? 1 : 0);
                int failures = current.failures() + (item.getOutcome() == RouteExperienceOutcome.SUCCESS ? 0 : 1);
                int delays = current.totalDelays() + (item.getDelayMinutes() == null ? 0 : item.getDelayMinutes());
                int lowRatings = current.lowRatings() + (item.getRating() != null && item.getRating() <= 2 ? 1 : 0);
                return new MemorySignal(successes, failures, delays, lowRatings);
            });
        }
        return new RouteMemory(experiences.size(), signals);
    }

    private RouteExperienceResponse mapExperience(RouteExperience experience) {
        return new RouteExperienceResponse(
                experience.getId(),
                experience.getTripId(),
                experience.getStationId(),
                experience.getOrigin(),
                experience.getDestination(),
                experience.getOutcome(),
                experience.getDetail(),
                experience.getRating(),
                experience.getDelayMinutes(),
                experience.getCreatedAt()
        );
    }

    private String routeKey(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
        return normalized.isBlank() ? "default" : normalized;
    }

    private String normalizedOptimization(String optimizeFor) {
        if (optimizeFor == null) return "TIME";
        String normalized = optimizeFor.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "COST", "BALANCED" -> normalized;
            default -> "TIME";
        };
    }

    private String normalizedAutonomyMode(String autonomyMode) {
        if (autonomyMode == null) return "ASK_BEFORE_ACTIONS";
        String normalized = autonomyMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RECOMMEND_ONLY", "FULL_AUTOPILOT" -> normalized;
            default -> "ASK_BEFORE_ACTIONS";
        };
    }

    private String normalizedIdempotencyKey(String key) {
        if (key != null && !key.isBlank()) return key.trim();
        return "TRIP-" + UUID.randomUUID();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String displayDeadline(String deadline) {
        return deadline == null || deadline.isBlank() ? "estimated ETA" : deadline;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record BaseRoute(
            OsrmRoute route,
            OsrmClient.RouteEngine engine
    ) {}

    private record RouteBounds(
            double minimumLatitude,
            double maximumLatitude,
            double minimumLongitude,
            double maximumLongitude
    ) {
        private static final RouteBounds INDIA = new RouteBounds(5, 39, 67, 99);

        boolean contains(double latitude, double longitude) {
            return latitude >= minimumLatitude && latitude <= maximumLatitude
                    && longitude >= minimumLongitude && longitude <= maximumLongitude;
        }
    }

    private record PlanningContext(
            BaseRoute baseRoute,
            List<Candidate> candidates,
            List<Candidate> selected,
            ChargingRouteOptimizer.OptimizationResult optimized,
            OsrmRoute finalRoute,
            OsrmClient.RouteEngine finalRouteEngine,
            OsrmClient.MatrixSelection matrixSelection,
            int dbBoundCandidates,
            int postCorridorCandidates,
            double corridorKm
    ) {}

    private record CandidateDiscovery(
            List<Candidate> candidates,
            int dbBoundCandidates,
            int postCorridorCandidates,
            double corridorKm
    ) {}

    private record CandidateEvaluation(
            List<Candidate> candidates,
            int dbBoundCandidates
    ) {}

    private record Candidate(
            ChargingStation station,
            ChargingConnector connector,
            double distanceFromOriginKm,
            double distanceToDestinationKm,
            double detourKm,
            double routeOffsetKm,
            int waitMinutes,
            double impactMinutes,
            boolean restFriendly,
            String selectionReason
    ) {}

    private record Itinerary(
            double distanceKm,
            int drivingMinutes,
            List<Candidate> selected,
            List<Double> legDistancesKm
    ) {}

    private record MemorySignal(int successes, int failures, int totalDelays, int lowRatings) {
        static final MemorySignal EMPTY = new MemorySignal(0, 0, 0, 0);
        double averageDelayMinutes() {
            int total = successes + failures;
            return total == 0 ? 0 : (double) totalDelays / total;
        }
    }

    private record RouteMemory(int totalExperiences, Map<Long, MemorySignal> stationSignals) {
        String summary() {
            if (totalExperiences == 0) return "No previous journey experience exists for this corridor yet.";
            return totalExperiences + " past journey outcome" + (totalExperiences == 1 ? "" : "s")
                    + " were retrieved to score reliability, queue delays and charger faults on this corridor.";
        }
    }

    @Transactional
    public void resetDemoStations() {
        List<ChargingStation> stations = stationRepository.findAll();
        for (ChargingStation s : stations) {
            if (s.isDemoData()) {
                s.setStatus(StationStatus.ACTIVE);
                s.setEmergencyDisabled(false);
                String seedKey = s.getDemoSeedKey();
                boolean districtHub = seedKey != null && seedKey.startsWith("SOI-");
                boolean canonicalHub = seedKey != null && seedKey.endsWith("_DEMO_01");
                int stateBucket = districtHub
                        ? Math.floorMod(seedKey.hashCode() * 31, 100) : 0;
                s.setAvailability(!districtHub ? StationAvailability.AVAILABLE
                        : stateBucket < 80 ? StationAvailability.AVAILABLE
                        : stateBucket < 90 ? StationAvailability.CHARGING : StationAvailability.UNAVAILABLE);
                s.setOccupancyPercent(!districtHub ? (canonicalHub ? 15 : 20)
                        : stateBucket < 80 ? 20 + Math.floorMod(seedKey.hashCode(), 36)
                        : stateBucket < 90 ? 85 + Math.floorMod(seedKey.hashCode(), 11) : 0);
                s.setQueueCount(districtHub && stateBucket >= 80 && stateBucket < 90
                        ? 2 + Math.floorMod(seedKey.hashCode(), 4) : 0);
                if (s.getConnectors() != null) {
                    for (int connectorIndex = 0; connectorIndex < s.getConnectors().size(); connectorIndex++) {
                        ChargingConnector c = s.getConnectors().get(connectorIndex);
                        ChargerStatus restoredStatus = !districtHub || stateBucket < 80 ? ChargerStatus.ONLINE
                                : stateBucket < 90 && connectorIndex == 0 ? ChargerStatus.CHARGING
                                : stateBucket < 90 ? ChargerStatus.ONLINE
                                : stateBucket < 95 ? ChargerStatus.MAINTENANCE : ChargerStatus.FAULT;
                        c.setStatus(restoredStatus);
                        c.setAvailable(restoredStatus == ChargerStatus.ONLINE);
                        c.setMaintenanceMode(restoredStatus == ChargerStatus.MAINTENANCE);
                        c.setFaultCode(restoredStatus == ChargerStatus.FAULT ? "SYNTHETIC_DEMO_FAULT" : null);
                        c.setHealthScore(restoredStatus == ChargerStatus.FAULT ? 38
                                : restoredStatus == ChargerStatus.MAINTENANCE ? 60
                                : restoredStatus == ChargerStatus.CHARGING ? 94 : 98);
                    }
                }
            }
        }
        stationRepository.saveAll(stations);

        Map<String, double[]> vehicleState = Map.of(
                "DEMO-EV-001", new double[]{85, 260},
                "DEMO-EV-002", new double[]{88, 435},
                "DEMO-EV-003", new double[]{80, 310},
                "DEMO-EV-004", new double[]{92, 240},
                "DEMO-EV-005", new double[]{75, 275},
                "DEMO-EV-006", new double[]{85, 332});
        List<Vehicle> restoredVehicles = new ArrayList<>();
        Set<Long> demoUserIds = new HashSet<>();
        vehicleState.forEach((registration, values) -> vehicleRepository.findByRegistrationNumber(registration)
                .ifPresent(vehicle -> {
                    vehicle.setBatteryPercent((int) values[0]);
                    vehicle.setRemainingRangeKm(values[1]);
                    vehicle.setCharging(false);
                    vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
                    restoredVehicles.add(vehicle);
                    demoUserIds.add(vehicle.getUserId());
                }));
        vehicleRepository.saveAll(restoredVehicles);
        if (!demoUserIds.isEmpty()) {
            experienceRepository.deleteByUserIdIn(demoUserIds);
        }
    }

    private record VehicleEvaluation(
            Vehicle vehicle,
            AutopilotPlanResponse plan,
            VehicleRecommendationOptionResponse option
    ) {}
}
