package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.AutopilotRecoveryResponse;
import com.vidyut.autopilot.entity.*;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.dto.*;
import com.vidyut.routing.service.LocationResolver;
import com.vidyut.routing.service.RouteCorridorService;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/** Computes complete SAFE options. Selection/orchestration belongs to the EV Agent. */
@Service @RequiredArgsConstructor
public class SafeRecoveryPlanner {
    public static final double BRIDGE_MARGIN_SOC = 3;
    private final RecoveryRoadService roads;
    private final AutopilotPositionService positions;
    private final ChargingStationRepository stations;
    private final LocationResolver locations;
    private final RouteCorridorService corridor;
    private final ChargingRouteOptimizer optimizer;
    private final VehicleChargingProfileService profiles;
    @Value("${vidyut.demo-data.enabled:false}") private boolean demoDataEnabled;

    public AutopilotRecoveryResponse snapshot(AutopilotTrip trip, Vehicle vehicle, AutopilotStop failed) {
        double capacity;
        try { capacity = Double.parseDouble(vehicle.getBatteryCapacity().replaceAll("[^0-9.]", "")); }
        catch (RuntimeException e) { throw new BadRequestException("VEHICLE_DATA_REQUIRED: battery capacity is unknown"); }
        Double efficiency = vehicle.getEfficiencyWhPerKm();
        if (!Double.isFinite(capacity) || capacity < 10 || capacity > 250 || efficiency == null || !Double.isFinite(efficiency) || efficiency < 50 || efficiency > 500
                || !Double.isFinite(trip.getCurrentBatteryPercent()) || trip.getCurrentBatteryPercent() < 0
                || trip.getCurrentBatteryPercent() > 100 || !Double.isFinite(trip.getMinimumArrivalBatteryPercent())
                || !Double.isFinite(trip.getMaximumChargingBudget()) || trip.getMaximumChargingBudget()<0
                || trip.getMinimumArrivalBatteryPercent() < 0 || trip.getMinimumArrivalBatteryPercent() >= 100) {
            throw new BadRequestException("VEHICLE_DATA_REQUIRED: valid battery, efficiency and reserve are required");
        }
        return AutopilotRecoveryResponse.builder().state("INCIDENT_DETECTED")
                .capturedAt(AutopilotPositionService.now()).positionRecordedAt(trip.getPositionRecordedAt())
                .positionSource(trip.getPositionSource()).currentLatitude(trip.getCurrentLatitude()).currentLongitude(trip.getCurrentLongitude())
                .currentSoc(trip.getCurrentBatteryPercent()).batteryCapacityKwh(capacity).efficiencyWhPerKm(efficiency)
                .reserveSoc(trip.getMinimumArrivalBatteryPercent()).safetyMarginSoc(BRIDGE_MARGIN_SOC)
                .safeReachableDistanceKm(Math.max(0, trip.getCurrentBatteryPercent()-trip.getMinimumArrivalBatteryPercent()) / 100 * capacity / (efficiency/1000))
                .compatibleConnectors(connectors(vehicle)).failedStationId(failed.getStationId()).failedConnectorId(failed.getConnectorId()).build();
    }

    public List<RecoveryPlan> options(AutopilotTrip trip, Vehicle vehicle, AutopilotStop failed,
            List<AutopilotStop> originalStops, AutopilotRecoveryResponse state) {
        Coordinate current = positions.current(trip), destination = locations.resolve(trip.getDestination());
        if (state.getCurrentSoc() <= state.getReserveSoc()) return List.of();
        RecoveryRoadService.RoadRoute base = roads.route(List.of(current, destination));
        Baseline baseline = baseline(current, destination, originalStops);
        if (arrival(state, base.route().distance()/1000, state.getCurrentSoc()) >= state.getReserveSoc()) {
            return List.of(complete(trip, vehicle, failed, state, List.of(), List.of(), baseline,
                    remainingBudget(trip, originalStops), "DIRECT_DESTINATION", null));
        }
        Long nextConnector = originalStops.stream().filter(s -> s.getSequenceNumber() > failed.getSequenceNumber()
                        && (s.getStatus() == AutopilotStopStatus.PLANNED || s.getStatus() == AutopilotStopStatus.RESERVED))
                .min(Comparator.comparingInt(AutopilotStop::getSequenceNumber)).map(AutopilotStop::getConnectorId).orElse(null);
        List<Candidate> candidates = discover(vehicle, failed, base.route(), nextConnector);
        List<Candidate> nearby = candidates.stream().filter(c -> RecoveryRoadService.distanceKm(current, c.point())
                        <= state.getSafeReachableDistanceKm()).toList();
        // Evaluate all nearby candidates in bounded road-matrix batches. Geometry
        // distance only bounds discovery; it never establishes feasibility.
        List<Reachable> reachable = new ArrayList<>();
        for (int offset = 0; offset < nearby.size(); offset += 60) {
            List<Candidate> batch = nearby.subList(offset, Math.min(offset + 60, nearby.size()));
            List<Coordinate> points = new ArrayList<>(List.of(current));
            batch.forEach(c -> points.add(c.point()));
            OsrmTableResponse matrix = roads.matrix(points, base.engine());
            for (int i = 0; i < batch.size(); i++) {
                Double meters = matrix.distances().get(0).get(i+1), seconds = matrix.durations().get(0).get(i+1);
                if (meters == null || seconds == null || !RecoveryRoadService.finiteNonnegative(meters)
                        || !RecoveryRoadService.finiteNonnegative(seconds)) continue;
                double km = meters/1000;
                if (arrival(state, km, state.getCurrentSoc()) + 1e-8 >= state.getReserveSoc()) reachable.add(new Reachable(batch.get(i), km));
            }
        }
        // A reachable next planned connector is preferred. Otherwise rank only
        // already-feasible bridge options, nearest road distance first.
        reachable.sort(Comparator.comparingInt((Reachable r) -> Objects.equals(r.candidate.connector().getId(), nextConnector) ? 0 : 1)
                .thenComparingDouble(Reachable::km).thenComparingDouble(r -> r.candidate.offsetKm())
                .thenComparingInt(r -> waitMinutes(r.candidate.station()))
                .thenComparingDouble(r -> -r.candidate.connector().getPowerKw())
                .thenComparingDouble(r -> r.candidate.station().getPricePerKwh()));
        List<RecoveryPlan> plans = new ArrayList<>();
        for (Reachable option : reachable) {
            try {
                Candidate bridge = option.candidate();
                // Verify the actual current-position -> charger route before it
                // can become a complete candidate, including router snapping.
                double realKm = roads.route(List.of(current, bridge.point())).route().distance()/1000;
                if (arrival(state, realKm, state.getCurrentSoc()) + 1e-8 < state.getReserveSoc()) continue;
                List<Candidate> downstream = candidates.stream().filter(c -> !c.station().getId().equals(bridge.station().getId())
                        && c.progressKm() > bridge.progressKm() + 0.1).sorted(Comparator.comparingDouble(Candidate::progressKm)).toList();
                List<Candidate> network = new ArrayList<>(List.of(bridge));
                network.addAll(spread(downstream, 58));
                List<Coordinate> points = new ArrayList<>(List.of(current));
                network.forEach(c -> points.add(c.point()));
                points.add(destination);
                OsrmTableResponse matrix = roads.matrix(points, base.engine());
                var profile = profiles.forVehicle(vehicle);
                double budget = remainingBudget(trip, originalStops);
                Integer remainingMinutes = deadlineMinutes(trip);
                var request = new ChargingRouteOptimizer.OptimizationRequest(network.stream().map(c ->
                        new ChargingRouteOptimizer.ChargingOption(c.station().getId(), c.progressKm(), c.offsetKm(),
                                compatiblePower(c.connector(), vehicle), c.station().getPricePerKwh(), waitMinutes(c.station()), c.station().getRating())).toList(),
                        matrix, state.getBatteryCapacityKwh(), state.getEfficiencyWhPerKm()/1000, state.getCurrentSoc(), state.getReserveSoc(),
                        budget, trip.getOptimizeFor(), profile.maximumDcPowerKw(), profile.efficiency(), profile.curve(), remainingMinutes);
                var optimized = optimizer.optimizeRecovery(request, BRIDGE_MARGIN_SOC);
                Map<Long, Candidate> byId = new HashMap<>();
                network.forEach(c -> byId.put(c.station().getId(), c));
                List<Candidate> selected = optimized.stops().stream().map(s -> byId.get(s.option().stationId())).toList();
                String strategy = Objects.equals(bridge.connector().getId(), nextConnector) ? "DIRECT_NEXT_STOP" : "BRIDGE_RECOVERY";
                plans.add(complete(trip, vehicle, failed, state, selected, optimized.stops().stream()
                        .map(ChargingRouteOptimizer.StopDecision::targetBatteryPercent).toList(), baseline, budget, strategy, null));
                if (plans.size() >= 4) break;
            } catch (BadRequestException | com.vidyut.routing.exception.OsrmException unsafeOrUnavailable) {
                // This charger can be nearby yet have no safe complete onward
                // chain, or real route legs may invalidate its matrix estimate.
            }
        }
        return List.copyOf(plans);
    }

    public RecoveryPlan revalidate(AutopilotTrip trip, Vehicle vehicle, AutopilotStop failed,
            AutopilotRecoveryResponse snapshot, RecoveryPlan plan, List<AutopilotStop> existing) {
        Coordinate current = positions.current(trip);
        if (Math.abs(trip.getCurrentBatteryPercent()-snapshot.getCurrentSoc()) > 1e-8
                || Math.abs(trip.getMinimumArrivalBatteryPercent()-snapshot.getReserveSoc()) > 1e-8
                || snapshot.getCurrentLatitude() == null || snapshot.getCurrentLongitude() == null
                || RecoveryRoadService.distanceKm(current, new Coordinate(snapshot.getCurrentLatitude(), snapshot.getCurrentLongitude())) > 0.01) {
            throw new BadRequestException("RECOVERY_STATE_CHANGED: refresh the safe recovery proposal before execution");
        }
        AutopilotRecoveryResponse currentModel = snapshot(trip, vehicle, failed);
        if (currentModel.getBatteryCapacityKwh() != snapshot.getBatteryCapacityKwh()
                || currentModel.getEfficiencyWhPerKm() != snapshot.getEfficiencyWhPerKm()
                || !currentModel.getCompatibleConnectors().equals(snapshot.getCompatibleConnectors())) {
            throw new BadRequestException("RECOVERY_STATE_CHANGED: vehicle energy or connector profile changed");
        }
        List<Candidate> chosen = plan.stops().stream().map(s -> {
            ChargingStation station = stations.findById(s.getStationId()).orElseThrow(() -> new BadRequestException("Recovery station no longer exists"));
            ChargingConnector connector = station.getConnectors().stream().filter(c -> c.getId().equals(s.getConnectorId()) && eligible(station, c, vehicle, failed))
                    .findFirst().orElseThrow(() -> new BadRequestException("Recovery connector is no longer safely available"));
            return new Candidate(station, connector, 0, 0);
        }).toList();
        return complete(trip, vehicle, failed, snapshot, chosen, plan.stops().stream().map(AutopilotStop::getTargetBatteryPercent).toList(),
                new Baseline(plan.originalRemainingDistanceKm(), plan.originalRemainingMinutes(), plan.originalRemainingCost()),
                remainingBudget(trip, existing), plan.strategy(), plan.id());
    }

    private RecoveryPlan complete(AutopilotTrip trip, Vehicle vehicle, AutopilotStop failed, AutopilotRecoveryResponse state,
            List<Candidate> selected, List<Double> targets, Baseline baseline, double budget, String strategy, String planId) {
        List<Coordinate> waypoints = new ArrayList<>(List.of(positions.current(trip)));
        selected.forEach(c -> waypoints.add(c.point()));
        waypoints.add(locations.resolve(trip.getDestination()));
        var routed = roads.route(waypoints);
        List<AutopilotStop> stops = new ArrayList<>();
        double soc = state.getCurrentSoc(), cumulative = trip.getDistanceTravelledKm(), cost = 0;
        int charging = 0, waiting = 0, connections = 0;
        var profile = profiles.forVehicle(vehicle);
        for (int i = 0; i < selected.size(); i++) {
            Candidate c = selected.get(i);
            double km = routed.route().legs().get(i).distance()/1000;
            soc = arrival(state, km, soc);
            requireReserve(soc, state.getReserveSoc());
            cumulative += km;
            double nextEnergySoc = routed.route().legs().get(i+1).distance()/1000 * state.getEfficiencyWhPerKm()/1000 / state.getBatteryCapacityKwh() * 100;
            double required = Math.ceil(state.getReserveSoc() + nextEnergySoc + (i == 0 ? BRIDGE_MARGIN_SOC : 0));
            double target = Math.max(soc, i == 0 ? required : Math.max(required, targets.get(i)));
            if (target > 95 && target > soc + 1e-8) throw new BadRequestException("NO_SAFE_RECOVERY_ROUTE: charging limit exceeded");
            var charge = optimizer.estimateCharge(state.getBatteryCapacityKwh(), compatiblePower(c.connector(), vehicle), profile.maximumDcPowerKw(),
                    profile.efficiency(), profile.curve(), soc, target);
            double chargeCost = Math.max(0, target-soc)/100 * state.getBatteryCapacityKwh() * c.station().getPricePerKwh();
            int queue = waitMinutes(c.station());
            stops.add(AutopilotStop.builder().tripId(trip.getId()).sequenceNumber(i+1).stationId(c.station().getId())
                    .stationName(c.station().getName()).stationAddress(c.station().getAddress()).connectorId(c.connector().getId())
                    .chargerCode(c.connector().getChargerCode()).connectorType(c.connector().getType().name()).powerKw(c.connector().getPowerKw())
                    .effectivePowerKw(charge.effectivePowerKw()).distanceFromOriginKm(cumulative).routeOffsetKm(0)
                    .arrivalBatteryPercent(soc).targetBatteryPercent(target).estimatedWaitMinutes(queue).chargingMinutes(charge.minutes())
                    .connectionMinutes(4).estimatedCost(chargeCost).demoData(c.station().isDemoData()).status(AutopilotStopStatus.PLANNED)
                    .selectionType(i == 0 ? "REROUTED_REPLACEMENT" : "PRIMARY")
                    .selectionReason(i == 0 ? strategy + ": verified road reachability from the captured vehicle position" : "Verified remaining-journey charging leg")
                    .replacesStationId(i == 0 ? failed.getStationId() : null).replacesStationName(i == 0 ? failed.getStationName() : null)
                    .originalStopIndex(i == 0 ? failed.getSequenceNumber() : null).rerouteReason(i == 0 ? "CHARGER_FAULT" : null).build());
            soc = target; cost += chargeCost; charging += charge.minutes(); waiting += queue; connections += 4;
        }
        soc = arrival(state, routed.route().legs().get(selected.size()).distance()/1000, soc);
        requireReserve(soc, state.getReserveSoc());
        int drive = (int)Math.ceil(routed.route().duration()/60);
        if (!Double.isFinite(cost) || cost > budget + 0.001) throw new BadRequestException("NO_SAFE_RECOVERY_ROUTE: budget exceeded");
        Integer remainingMinutes = deadlineMinutes(trip);
        if (remainingMinutes!=null && drive+charging+waiting+connections > remainingMinutes)
            throw new BadRequestException("NO_SAFE_RECOVERY_ROUTE: arrival deadline cannot be maintained");
        return new RecoveryPlan(planId == null ? UUID.randomUUID().toString() : planId, strategy, List.copyOf(stops), routed.route(), routed.engine(),
                soc, drive, charging, waiting, connections, cost, baseline.km(), baseline.minutes(), baseline.cost(),
                routed.route().legs().get(0).distance()/1000 * state.getEfficiencyWhPerKm()/1000);
    }

    private List<Candidate> discover(Vehicle vehicle, AutopilotStop failed, OsrmRoute route, Long nextConnector) {
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (List<Double> xy : route.geometry().coordinates()) { minLat=Math.min(minLat,xy.get(1)); maxLat=Math.max(maxLat,xy.get(1)); minLon=Math.min(minLon,xy.get(0)); maxLon=Math.max(maxLon,xy.get(0)); }
        List<Candidate> found = new ArrayList<>();
        for (ChargingStation s : stations.findPublishedStationsWithinBounds(minLat-1,maxLat+1,minLon-1.2,maxLon+1.2,demoDataEnabled)) {
            ChargingConnector c = s.getConnectors().stream().filter(connector -> eligible(s,connector,vehicle,failed))
                    .max(Comparator.comparingInt((ChargingConnector connector) -> java.util.Objects.equals(connector.getId(),nextConnector) ? 1 : 0).thenComparingDouble(connector -> compatiblePower(connector,vehicle))).orElse(null);
            if (c == null) continue;
            var match = corridor.match(new Coordinate(s.getLatitude(),s.getLongitude()),route.geometry());
            if (match.offsetKm() <= 100) found.add(new Candidate(s,c,match.progressKm(),match.offsetKm()));
        }
        return found;
    }

    private Baseline baseline(Coordinate current, Coordinate destination, List<AutopilotStop> original) {
        List<AutopilotStop> remaining = original.stream().filter(s -> s.getStatus()==AutopilotStopStatus.RESERVED || s.getStatus()==AutopilotStopStatus.PLANNED)
                .sorted(Comparator.comparingInt(AutopilotStop::getSequenceNumber)).toList();
        double cost = remaining.stream().mapToDouble(AutopilotStop::getEstimatedCost).sum();
        try {
            List<Coordinate> points = new ArrayList<>(List.of(current));
            for (AutopilotStop s : remaining) { ChargingStation site=stations.findById(s.getStationId()).orElseThrow(); points.add(new Coordinate(site.getLatitude(),site.getLongitude())); }
            points.add(destination);
            var route = roads.route(points).route();
            return new Baseline(route.distance()/1000, (int)Math.ceil(route.duration()/60) + remaining.stream()
                    .mapToInt(s -> s.getChargingMinutes()+s.getEstimatedWaitMinutes()+s.getConnectionMinutes()).sum(), cost);
        } catch (RuntimeException unavailable) { return new Baseline(null,null,cost); }
    }

    static Integer deadlineMinutes(AutopilotTrip trip) {
        if (trip.getArrivalDeadline()==null || trip.getArrivalDeadline().isBlank()) return null;
        if (trip.getArrivalDeadlineAt()==null) throw new BadRequestException("DEADLINE_REQUIRED: refresh this legacy journey with an absolute arrival deadline");
        long remaining = java.time.Duration.between(java.time.LocalDateTime.now(), trip.getArrivalDeadlineAt()).toMinutes();
        if (remaining < 0) throw new BadRequestException("NO_SAFE_RECOVERY_ROUTE: configured arrival deadline has passed");
        return (int)Math.min(Integer.MAX_VALUE, remaining);
    }

    static void requireReserve(double arrival, double reserve) {
        if (!Double.isFinite(arrival) || arrival + 1e-8 < reserve) throw new BadRequestException("NO_SAFE_RECOVERY_ROUTE: a road leg violates the configured reserve");
    }
    static double arrival(AutopilotRecoveryResponse s, double roadKm, double departure) { return departure - roadKm*s.getEfficiencyWhPerKm()/1000/s.getBatteryCapacityKwh()*100; }
    static double remainingBudget(AutopilotTrip t,List<AutopilotStop> stops) { return Math.max(0,t.getMaximumChargingBudget()-stops.stream().filter(s->s.getStatus()==AutopilotStopStatus.COMPLETED).mapToDouble(AutopilotStop::getEstimatedCost).sum()); }
    static List<String> connectors(Vehicle v) {
        if (v.getSupportedConnectors()!=null && !v.getSupportedConnectors().isEmpty()) return v.getSupportedConnectors().stream().map(Enum::name).sorted().toList();
        return v.getConnectorType()==null ? List.of() : List.of(v.getConnectorType().toUpperCase(Locale.ROOT).replace("TYPE 2", "TYPE2").replace("GB/T", "GB_T"));
    }
    static boolean eligible(ChargingStation s,ChargingConnector c,Vehicle v,AutopilotStop failed) {
        return s.getStatus()==StationStatus.ACTIVE && s.getAvailability()==StationAvailability.AVAILABLE && !s.isEmergencyDisabled()
                && Double.isFinite(s.getPricePerKwh()) && s.getPricePerKwh()>=0
                && c.getStatus()==ChargerStatus.ONLINE && c.isAvailable() && !c.isMaintenanceMode()
                && c.getId()!=null && connectors(v).contains(c.getType().name()) && compatiblePower(c,v)>0
                && (failed.getConnectorId()!=null ? !failed.getConnectorId().equals(c.getId()) : !failed.getStationId().equals(s.getId()));
    }
    static double compatiblePower(ChargingConnector c,Vehicle v) {
        boolean ac=c.getType()==ConnectorType.TYPE1 || c.getType()==ConnectorType.TYPE2;
        Double max=ac ? v.getMaxAcChargePowerKw() : v.getMaxDcChargePowerKw();
        return Math.min(c.getPowerKw(), max!=null && Double.isFinite(max) && max>0 ? max : ac ? 7.2 : 50);
    }
    static int waitMinutes(ChargingStation s) { return Math.max(0,s.getQueueCount()*7)+(int)Math.round(Math.max(0,s.getOccupancyPercent())/20*3); }
    static List<Candidate> spread(List<Candidate> list,int limit) {
        if(list.size()<=limit)return list;
        List<Candidate> result=new ArrayList<>();
        for(int i=0;i<limit;i++) result.add(list.get((int)Math.round(i*(list.size()-1.0)/(limit-1))));
        return result;
    }
    record Candidate(ChargingStation station,ChargingConnector connector,double progressKm,double offsetKm) {
        Coordinate point(){return new Coordinate(station.getLatitude(),station.getLongitude());}
    }
    record Reachable(Candidate candidate,double km) {}
    record Baseline(Double km,Integer minutes,double cost) {}
}
