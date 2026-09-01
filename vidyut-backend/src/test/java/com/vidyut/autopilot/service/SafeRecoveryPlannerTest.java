package com.vidyut.autopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.entity.*;
import com.vidyut.autopilot.dto.AutopilotRecoveryResponse;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.*;
import com.vidyut.routing.service.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafeRecoveryPlannerTest {
    final RecoveryRoadService roads = mock(RecoveryRoadService.class);
    final ChargingStationRepository stations = mock(ChargingStationRepository.class);
    final LocationResolver locations = mock(LocationResolver.class);
    final AutopilotPositionService positions = new AutopilotPositionService(new ObjectMapper().findAndRegisterModules(), new RouteCorridorService());
    final SafeRecoveryPlanner planner = new SafeRecoveryPlanner(roads, positions, stations, locations,
            new RouteCorridorService(), new ChargingRouteOptimizer(), new VehicleChargingProfileService());
    final Coordinate current = new Coordinate(28,77), bridge = new Coordinate(27.9,77), failedPoint = new Coordinate(27.5,77), kota = new Coordinate(26.8,77), destination = new Coordinate(25,77);
    final Vehicle vehicle = Vehicle.builder().id(1L).batteryCapacity("66.5 kWh").efficiencyWhPerKm(170.0)
            .connectorType("CCS2").maxDcChargePowerKw(130.0).maxAcChargePowerKw(11.0).chargingEfficiency(.9).build();
    final AutopilotTrip trip = AutopilotTrip.builder().id(1L).vehicleId(1L).currentBatteryPercent(39).minimumArrivalBatteryPercent(15)
            .maximumChargingBudget(2000).destination("Bhopal").optimizeFor("TIME")
            .currentLatitude(28.0).currentLongitude(77.0).positionRecordedAt(AutopilotPositionService.now()).positionSource("GPS").build();
    final AutopilotStop failed = AutopilotStop.builder().id(1L).stationId(10L).connectorId(100L).sequenceNumber(1).stationName("Dausa")
            .status(AutopilotStopStatus.RESERVED).estimatedCost(300).build();
    final AutopilotStop next = AutopilotStop.builder().id(2L).stationId(30L).connectorId(300L).sequenceNumber(2).stationName("Kota")
            .status(AutopilotStopStatus.RESERVED).estimatedCost(400).build();
    ChargingStation bridgeSite, kotaSite;
    boolean firstRoadTooLong;

    @BeforeEach void setUp() {
        bridgeSite = site(20, bridge); kotaSite = site(30,kota);
        when(locations.resolve("Bhopal")).thenReturn(destination);
        when(stations.findPublishedStationsWithinBounds(anyDouble(),anyDouble(),anyDouble(),anyDouble(),anyBoolean())).thenReturn(List.of(bridgeSite,kotaSite));
        when(stations.findById(10L)).thenReturn(Optional.of(site(10,failedPoint)));
        when(stations.findById(20L)).thenReturn(Optional.of(bridgeSite)); when(stations.findById(30L)).thenReturn(Optional.of(kotaSite));
        when(roads.route(anyList())).thenAnswer(inv -> {
            List<Coordinate> points = inv.getArgument(0);
            List<OsrmLeg> legs = new ArrayList<>();
            for(int i=1;i<points.size();i++) {
                double km = distance(points.get(i-1),points.get(i));
                if(firstRoadTooLong && points.get(i-1).equals(current) && points.get(i).equals(bridge)) km=100;
                legs.add(new OsrmLeg(km*1000,km*60));
            }
            return new RecoveryRoadService.RoadRoute(new OsrmRoute(legs.stream().mapToDouble(OsrmLeg::distance).sum(),
                    legs.stream().mapToDouble(OsrmLeg::duration).sum(), new OsrmGeometry("LineString",points.stream()
                    .map(p->List.of(p.longitude(),p.latitude())).toList()),legs), OsrmClient.RouteEngine.PRIMARY);
        });
        when(roads.matrix(anyList(),any())).thenAnswer(inv -> {
            List<Coordinate> points=inv.getArgument(0);
            return new OsrmTableResponse("Ok",points.stream().map(a->points.stream().map(b->distance(a,b)*1000).toList()).toList(),
                    points.stream().map(a->points.stream().map(b->distance(a,b)*60).toList()).toList());
        });
    }

    @Test void bmwCannotJump160KmAt39PercentAndUsesMinimumBridgeCharge() {
        AutopilotRecoveryResponse snapshot=planner.snapshot(trip,vehicle,failed);
        assertThat(snapshot.getSafeReachableDistanceKm()).isCloseTo(93.88235,within(.0001));
        assertThat(SafeRecoveryPlanner.arrival(snapshot,160,39)).isLessThan(15);
        List<RecoveryPlan> options=planner.options(trip,vehicle,failed,List.of(failed,next),snapshot);
        assertThat(options).hasSize(1);
        RecoveryPlan plan=options.get(0);
        assertThat(plan.strategy()).isEqualTo("BRIDGE_RECOVERY");
        assertThat(plan.stops()).extracting(AutopilotStop::getConnectorId).containsExactly(200L,300L);
        assertThat(plan.stops().get(0).getArrivalBatteryPercent()).isCloseTo(33.37594,within(.0001));
        assertThat(plan.stops().get(0).getTargetBatteryPercent()).isEqualTo(49);
        assertEveryLegSafe(plan,snapshot);
        assertThat(plan.distanceKm()).isEqualTo(382);
        assertThat(plan.originalRemainingDistanceKm()).isEqualTo(400);
        assertThat(plan.totalMinutes()).isGreaterThan(plan.driveMinutes());
        assertThat(failed.getStatus()).isEqualTo(AutopilotStopStatus.RESERVED);
    }

    @Test void reachableNextPlannedConnectorIsReturnedAsDirectContinuation() {
        trip.setCurrentBatteryPercent(65);
        var options=planner.options(trip,vehicle,failed,List.of(failed,next),planner.snapshot(trip,vehicle,failed));
        assertThat(options.get(0).strategy()).isEqualTo("DIRECT_NEXT_STOP");
        assertThat(options.get(0).stops().get(0).getConnectorId()).isEqualTo(300L);
    }

    @Test void rejectsMatrixCandidateWhenActualRoadDistanceViolatesReserve() {
        firstRoadTooLong=true;
        assertThat(planner.options(trip,vehicle,failed,List.of(failed,next),planner.snapshot(trip,vehicle,failed))).isEmpty();
    }

    @Test void noReachableChargerDoesNotInventAPlan() {
        bridgeSite.getConnectors().get(0).setStatus(ChargerStatus.FAULT);
        assertThat(planner.options(trip,vehicle,failed,List.of(failed,next),planner.snapshot(trip,vehicle,failed))).isEmpty();
    }

    @Test void revalidationRejectsChangedSocOrConnectorHealth() {
        var snapshot=planner.snapshot(trip,vehicle,failed);
        var plan=planner.options(trip,vehicle,failed,List.of(failed,next),snapshot).get(0);
        trip.setCurrentBatteryPercent(38);
        assertThatThrownBy(()->planner.revalidate(trip,vehicle,failed,snapshot,plan,List.of(failed,next))).hasMessageContaining("STATE_CHANGED");
        trip.setCurrentBatteryPercent(39); bridgeSite.getConnectors().get(0).setStatus(ChargerStatus.FAULT);
        assertThatThrownBy(()->planner.revalidate(trip,vehicle,failed,snapshot,plan,List.of(failed,next))).hasMessageContaining("no longer safely available");
    }

    @Test void unknownOrStalePositionFailsClosed() {
        trip.setCurrentLatitude(null);
        assertThatThrownBy(()->planner.options(trip,vehicle,failed,List.of(failed,next),planner.snapshot(trip,vehicle,failed))).hasMessageContaining("POSITION_REQUIRED");
        trip.setCurrentLatitude(28.0); trip.setPositionRecordedAt(AutopilotPositionService.now().minusMinutes(3));
        assertThatThrownBy(()->planner.options(trip,vehicle,failed,List.of(failed,next),planner.snapshot(trip,vehicle,failed))).hasMessageContaining("stale");
    }

    @Test void exactFailedConnectorIsExcludedButHealthySiblingRemainsEligible() {
        var site=site(10,failedPoint); var sibling=site.getConnectors().get(0);
        assertThat(SafeRecoveryPlanner.eligible(site,sibling,vehicle,failed)).isFalse();
        sibling.setId(101L);
        assertThat(SafeRecoveryPlanner.eligible(site,sibling,vehicle,failed)).isTrue();
    }

    @Test void budgetAndExpiredDeadlineAreHardConstraints() {
        trip.setMaximumChargingBudget(1);
        assertThat(planner.options(trip,vehicle,failed,List.of(failed,next),planner.snapshot(trip,vehicle,failed))).isEmpty();
        trip.setArrivalDeadline("10:00"); trip.setArrivalDeadlineAt(java.time.LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(()->SafeRecoveryPlanner.deadlineMinutes(trip)).hasMessageContaining("deadline has passed");
    }

    @Test void reserveThresholdUsesUnroundedBattery() {
        var snapshot=planner.snapshot(trip,vehicle,failed);
        SafeRecoveryPlanner.requireReserve(SafeRecoveryPlanner.arrival(snapshot,snapshot.getSafeReachableDistanceKm(),39),15);
        assertThatThrownBy(()->SafeRecoveryPlanner.requireReserve(SafeRecoveryPlanner.arrival(snapshot,snapshot.getSafeReachableDistanceKm()+.01,39),15))
                .isInstanceOf(BadRequestException.class);
    }

    void assertEveryLegSafe(RecoveryPlan plan,AutopilotRecoveryResponse s) {
        double departure=s.getCurrentSoc();
        for(int i=0;i<plan.route().legs().size();i++) {
            double arrival=SafeRecoveryPlanner.arrival(s,plan.route().legs().get(i).distance()/1000,departure);
            assertThat(arrival).isGreaterThanOrEqualTo(s.getReserveSoc());
            if(i<plan.stops().size()) departure=plan.stops().get(i).getTargetBatteryPercent();
        }
    }
    ChargingStation site(long id,Coordinate p) {
        return ChargingStation.builder().id(id).name("Site "+id).latitude(p.latitude()).longitude(p.longitude()).pricePerKwh(12)
                .connectors(List.of(ChargingConnector.builder().id(id*10).type(ConnectorType.CCS2).powerKw(150).available(true).status(ChargerStatus.ONLINE).build())).build();
    }
    double distance(Coordinate a,Coordinate b) {
        if(a.equals(b))return 0;
        if(a.equals(current)&&b.equals(bridge))return 22;
        if(a.equals(current)&&b.equals(failedPoint))return 68;
        if(a.equals(current)&&b.equals(kota))return 160;
        if(a.equals(failedPoint)&&b.equals(kota))return 92;
        if(a.equals(bridge)&&b.equals(kota))return 120;
        if(a.equals(kota)&&b.equals(destination))return 240;
        return 400;
    }
}
