package com.vidyut.routing;

import com.vidyut.autopilot.entity.RouteExperience;
import com.vidyut.autopilot.entity.RouteExperienceOutcome;
import com.vidyut.autopilot.repository.RouteExperienceRepository;
import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.service.RoutingService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.service.ChargingStationService;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoutingServiceTest {
    @Autowired private RoutingService routingService;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ChargingStationService stationService;
    @Autowired private ChargingStationRepository stationRepository;
    @Autowired private RouteExperienceRepository experienceRepository;

    private static final AtomicInteger CHARGER_SEQUENCE = new AtomicInteger();

    @Test
    void outOfRangeTripRecommendsOnlyCompatibleAvailableStations() {
        long userId = 9101L;
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(userId).makeAndModel("Nexon EV")
                .registrationNumber("UP32RT9101").batteryCapacity("30 kWh").connectorType("CCS2")
                .batteryPercent(30).remainingRangeKm(55.0).build());
        stationService.createStation(StationCreateRequest.builder().name("CCS Route Stop")
                .address("Amausi, Lucknow").city("Lucknow").latitude(26.7606).longitude(80.8893)
                .pricePerKwh(13.0).connectorType(ConnectorType.CCS2).powerKw(60).bookingSlotMinutes(30).build(), 77L);

        var plan = routingService.planRoute(RoutePlanRequest.builder().origin("Lucknow")
                .destination("Kanpur").vehicleId(vehicle.getId()).currentBatteryPercent(30)
                .tripPurpose("MALL_VISIT").build(), userId);

        assertThat(plan.isDestinationWithinRange()).isFalse();
        assertThat(plan.getRecommendedChargingStops()).isNotEmpty();
        assertThat(plan.getTripPurpose()).isEqualTo("MALL_VISIT");
        assertThat(plan.getPurposeSummary()).containsIgnoringCase("shopping");
        assertThat(plan.getRecommendedChargingStops()).allSatisfy(stop -> {
            assertThat(stop.isConnectorMatched()).isTrue();
            assertThat(stop.getAvailableSlots()).isPositive();
        });
    }

    @Test
    void inRangeGeneralTripDoesNotAddAnUnnecessaryChargingStop() {
        long userId = 9102L;
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(userId).makeAndModel("Nexon EV Long Range")
                .registrationNumber("UP32RT9102").batteryCapacity("40 kWh").connectorType("CCS2")
                .batteryPercent(80).remainingRangeKm(240.0).build());

        var plan = routingService.planRoute(RoutePlanRequest.builder().origin("Lucknow")
                .destination("Kanpur").vehicleId(vehicle.getId()).currentBatteryPercent(80)
                .tripPurpose("GENERAL").build(), userId);

        assertThat(plan.isDestinationWithinRange()).isTrue();
        assertThat(plan.getRecommendedChargingStops()).isEmpty();
        assertThat(plan.getEstimatedArrivalBatteryPercent()).isGreaterThan(plan.getReserveBatteryPercent());
    }

    @Test
    void routingRejectsOfflineAndConnectorIncompatibleCandidates() {
        long userId = 9103L;
        Vehicle vehicle = shortRangeVehicle(userId, "UP32RT9103");
        ChargingStation compatible = saveStation("Compatible CCS2", 26.7606, 80.8893,
                ConnectorType.CCS2, StationStatus.ACTIVE, false, "Parking", 13);
        saveStation("Wrong Type2", 26.8000, 80.9100,
                ConnectorType.TYPE2, StationStatus.ACTIVE, false, "Parking", 9);
        saveStation("Offline CCS2", 26.7900, 80.9000,
                ConnectorType.CCS2, StationStatus.OFFLINE, false, "Parking", 8);

        var plan = outOfRangePlan(userId, vehicle, "GENERAL");

        assertThat(plan.getRecommendedChargingStops())
                .extracting(stop -> stop.getStation().getId())
                .containsExactly(compatible.getId());
    }

    @Test
    void restStopPurposePrefersFoodAndRestroomAmenities() {
        long userId = 9104L;
        Vehicle vehicle = shortRangeVehicle(userId, "UP32RT9104");
        saveStation("Basic Route Charger", 26.7900, 80.8700,
                ConnectorType.CCS2, StationStatus.ACTIVE, false, "Parking", 10);
        ChargingStation restFriendly = saveStation("Highway Rest Hub", 26.7600, 80.8200,
                ConnectorType.CCS2, StationStatus.ACTIVE, false,
                "Restaurant, restroom, cafe and lounge", 15);

        var plan = outOfRangePlan(userId, vehicle, "REST_STOP");

        assertThat(plan.getRecommendedChargingStops()).isNotEmpty();
        assertThat(plan.getRecommendedChargingStops().get(0).getStation().getId())
                .isEqualTo(restFriendly.getId());
        assertThat(plan.getRecommendedChargingStops().get(0).getReason())
                .containsIgnoringCase("rest and food");
    }

    @Test
    void destinationChargingPrefersAStationNearTheDestination() {
        long userId = 9105L;
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(userId).makeAndModel("Nexon EV")
                .registrationNumber("UP32RT9105").batteryCapacity("40 kWh").connectorType("CCS2")
                .batteryPercent(80).remainingRangeKm(240.0).build());
        saveStation("Lucknow Origin Charger", 26.8400, 80.9400,
                ConnectorType.CCS2, StationStatus.ACTIVE, false, "Parking", 8);
        ChargingStation destinationStation = saveStation("Kanpur Destination Charger", 26.4500, 80.3320,
                ConnectorType.CCS2, StationStatus.ACTIVE, false, "Mall parking", 16);

        var plan = routingService.planRoute(RoutePlanRequest.builder().origin("Lucknow")
                .destination("Kanpur Mall").vehicleId(vehicle.getId()).currentBatteryPercent(80)
                .tripPurpose("DESTINATION_CHARGING").build(), userId);

        assertThat(plan.getRecommendedChargingStops()).singleElement()
                .satisfies(stop -> assertThat(stop.getStation().getId()).isEqualTo(destinationStation.getId()));
    }

    @Test
    void repeatedFaultHistoryDeprioritizesAnOtherwiseGoodStation() {
        long userId = 9106L;
        Vehicle vehicle = shortRangeVehicle(userId, "UP32RT9106");
        ChargingStation unreliable = saveStation("Historically Faulty", 26.7600, 80.8200,
                ConnectorType.CCS2, StationStatus.ACTIVE, false, "Parking", 8);
        ChargingStation reliable = saveStation("Reliable Alternative", 26.7800, 80.8500,
                ConnectorType.CCS2, StationStatus.ACTIVE, false, "Parking", 14);
        for (int index = 0; index < 3; index++) {
            experienceRepository.save(RouteExperience.builder().userId(userId).stationId(unreliable.getId())
                    .origin("Lucknow").destination("Kanpur").originKey("lucknow").destinationKey("kanpur")
                    .outcome(RouteExperienceOutcome.CHARGER_FAULT).detail("Fault scenario " + index).build());
        }

        var plan = outOfRangePlan(userId, vehicle, "GENERAL");

        assertThat(plan.getPastExperiencesUsed()).isEqualTo(3);
        assertThat(plan.getRecommendedChargingStops()).isNotEmpty();
        assertThat(plan.getRecommendedChargingStops().get(0).getStation().getId()).isEqualTo(reliable.getId());
        assertThat(plan.getRecommendedChargingStops())
                .filteredOn(stop -> stop.getStation().getId().equals(unreliable.getId()))
                .allSatisfy(stop -> assertThat(stop.getReason()).contains("3 issue signal"));
    }

    private Vehicle shortRangeVehicle(long userId, String registrationNumber) {
        return vehicleRepository.save(Vehicle.builder().userId(userId).makeAndModel("Nexon EV")
                .registrationNumber(registrationNumber).batteryCapacity("30 kWh").connectorType("CCS2")
                .batteryPercent(35).remainingRangeKm(70.0).build());
    }

    private RoutePlanRequest.RoutePlanRequestBuilder routeRequest(Vehicle vehicle, String purpose) {
        return RoutePlanRequest.builder().origin("Lucknow").destination("Kanpur")
                .vehicleId(vehicle.getId()).currentBatteryPercent(35).destinationDistanceKm(120.0)
                .tripPurpose(purpose);
    }

    private com.vidyut.routing.dto.RoutePlanResponse outOfRangePlan(long userId, Vehicle vehicle, String purpose) {
        return routingService.planRoute(routeRequest(vehicle, purpose).build(), userId);
    }

    private ChargingStation saveStation(String name, double latitude, double longitude, ConnectorType connectorType,
                                        StationStatus status, boolean emergencyDisabled, String amenities,
                                        double pricePerKwh) {
        ChargingStation station = ChargingStation.builder().name(name).address(name + ", Uttar Pradesh")
                .city("Lucknow").latitude(latitude).longitude(longitude).pricePerKwh(pricePerKwh)
                .amenities(amenities).workingHours("24x7").hostUserId(7000L)
                .status(status).availability(status == StationStatus.ACTIVE
                        ? StationAvailability.AVAILABLE : StationAvailability.UNAVAILABLE)
                .emergencyDisabled(emergencyDisabled).connectors(new ArrayList<>()).build();
        ChargingConnector connector = ChargingConnector.builder().station(station).type(connectorType).powerKw(60)
                .chargerCode("ROUTE-TEST-" + CHARGER_SEQUENCE.incrementAndGet())
                .available(status == StationStatus.ACTIVE).status(status == StationStatus.ACTIVE
                        ? ChargerStatus.ONLINE : ChargerStatus.OFFLINE).build();
        station.getConnectors().add(connector);
        return stationRepository.saveAndFlush(station);
    }
}
