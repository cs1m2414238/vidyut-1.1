package com.vidyut.autopilot.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.dto.AutopilotPositionRequest;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.routing.dto.*;
import com.vidyut.routing.service.RouteCorridorService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AutopilotPositionServiceTest {
    final AutopilotPositionService positions=new AutopilotPositionService(new ObjectMapper().findAndRegisterModules(),new RouteCorridorService());
    @Test void demoMovementUsesRoadProgressAndMatchingEnergyConsumption() {
        var trip=AutopilotTrip.builder().currentBatteryPercent(45).build();
        positions.setNavigation(trip,new OsrmRoute(100000,6000,new OsrmGeometry("LineString",List.of(List.of(77.0,28.0),List.of(78.0,28.0))),List.of(new OsrmLeg(100000,6000))));
        positions.initializeDemo(trip,new Coordinate(28,77));
        positions.advanceDemo(trip,6,66.5,.17);
        assertThat(trip.getCurrentBatteryPercent()).isCloseTo(39,within(.000001));
        assertThat(trip.getDistanceTravelledKm()).isCloseTo(23.470588,within(.00001));
        assertThat(trip.getCurrentLongitude()).isCloseTo(77.23470588,within(.00001));
        assertThat(trip.getPositionSource()).isEqualTo("DEMO_ROUTE_PROGRESS");
        assertThat(positions.current(trip)).isEqualTo(new Coordinate(28,trip.getCurrentLongitude()));
    }
    @Test void gpsAndSocAreCapturedTogetherAndOldUpdatesAreRejected() {
        var trip=new AutopilotTrip();
        var request=new AutopilotPositionRequest(28.1,77.2,39.0,AutopilotPositionService.now());
        positions.recordGps(trip,request);
        assertThat(trip.getPositionSource()).isEqualTo("GPS");
        assertThat(trip.getCurrentBatteryPercent()).isEqualTo(39);
        request.setRecordedAt(request.getRecordedAt().minusSeconds(1));
        assertThatThrownBy(()->positions.recordGps(trip,request)).hasMessageContaining("fresh");
    }
    @Test void unknownPositionCannotFallBackToTheFailedStation() {
        assertThatThrownBy(()->positions.current(new AutopilotTrip())).hasMessageContaining("POSITION_REQUIRED");
    }
}
