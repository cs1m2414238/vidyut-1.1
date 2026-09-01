package com.vidyut.autopilot;

import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.repository.AutopilotTripRepository;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.entity.EvWallet;
import com.vidyut.wallet.repository.EvWalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AutopilotServiceTest {

    private static final long USER_ID = 501L;

    @Autowired
    private AutopilotService autopilotService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ChargingStationRepository stationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AutopilotTripRepository tripRepository;

    @Autowired
    private EvWalletRepository walletRepository;

    @Test
    void plansBooksReroutesAndPaysWithoutDuplicateTrip() {
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .userId(USER_ID)
                .makeAndModel("Vidyut Test EV")
                .registrationNumber("UP78AP5010")
                .batteryCapacity("40.5 kWh")
                .connectorType("CCS2")
                .build());
        walletRepository.save(EvWallet.builder().userId(USER_ID).balance(1000).build());

        addStation("Kanpur Gateway", 26.5220, 80.0350, 13, 120, 1);
        addStation("Etawah Express", 26.7829, 79.0277, 12.5, 150, 0);
        addStation("Agra GreenCharge", 27.1767, 78.0081, 14.2, 150, 3);
        addStation("Mathura VoltPoint", 27.4924, 77.6737, 13.4, 180, 0);
        addStation("Jewar Energy Plaza", 28.1580, 77.5540, 12.9, 180, 1);
        addStation("Greater Noida Hub", 28.4744, 77.5040, 15, 240, 0);

        AutopilotTripRequest request = AutopilotTripRequest.builder()
                .vehicleId(vehicle.getId())
                .origin("Kanpur")
                .destination("Delhi")
                .goal("Reach Delhi safely before 6 PM for under ₹900")
                .arrivalDeadline("18:00")
                .tripPurpose("REST_STOP")
                .optimizeFor("TIME")
                .currentBatteryPercent(42)
                .minimumArrivalBatteryPercent(15)
                .maximumChargingBudget(900)
                .idempotencyKey("AUTOPILOT-TEST-501")
                .build();

        var proposal = autopilotService.previewTrip(USER_ID, request);
        assertThat(proposal.getStops()).isNotEmpty();
        assertThat(proposal.isWithinBudget()).isTrue();
        assertThat(proposal.isSafeArrivalReserve()).isTrue();
        assertThat(proposal.getBudgetRemaining()).isGreaterThanOrEqualTo(0);
        assertThat(proposal.getTripPurpose()).isEqualTo("REST_STOP");
        assertThat(proposal.getPurposeSummary()).containsIgnoringCase("rest");
        assertThat(proposal.getStops()).allSatisfy(stop -> assertThat(stop.getSelectionReason()).isNotBlank());
        assertThat(bookingRepository.count()).isZero();
        assertThat(tripRepository.count()).isZero();

        var launched = autopilotService.launchTrip(USER_ID, request);
        assertThat(launched.getStatus()).isEqualTo(AutopilotTripStatus.RESERVED);
        assertThat(launched.getStops()).isNotEmpty();
        assertThat(launched.getActiveBookingId()).isNotNull();
        assertThat(launched.getTimeline()).extracting("title")
                .contains("Vehicle connected", "Route analyzed", "Connector reserved");

        var duplicate = autopilotService.launchTrip(USER_ID, request);
        assertThat(duplicate.getId()).isEqualTo(launched.getId());

        var monitoring = autopilotService.startJourney(launched.getId(), USER_ID,
                AutopilotProgressRequest.builder().batteryDropPercent(5).build());
        assertThat(monitoring.getStatus()).isEqualTo(AutopilotTripStatus.MONITORING);
        assertThat(monitoring.getTelemetry().getBatteryPercent()).isEqualTo(37);

        Long oldBookingId = monitoring.getActiveBookingId();
        var rerouted = autopilotService.simulateChargerFault(launched.getId(), USER_ID);
        assertThat(rerouted.getStatus()).isEqualTo(AutopilotTripStatus.REROUTED);
        assertThat(rerouted.getActiveBookingId()).isNotEqualTo(oldBookingId);
        assertThat(bookingRepository.findById(oldBookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);

        var learnedProposal = autopilotService.previewTrip(USER_ID, request);
        assertThat(learnedProposal.getPastExperiencesUsed()).isPositive();
        assertThat(learnedProposal.getMemorySummary()).containsIgnoringCase("prior route");

        var completed = autopilotService.completeCharging(launched.getId(), USER_ID);
        int remainingStops = 10;
        while (completed.getStatus() != AutopilotTripStatus.COMPLETED && remainingStops-- > 0) {
            assertThat(completed.getStatus()).isEqualTo(AutopilotTripStatus.MONITORING);
            completed = autopilotService.completeCharging(launched.getId(), USER_ID);
        }
        assertThat(completed.getStatus()).isEqualTo(AutopilotTripStatus.COMPLETED);
        assertThat(completed.getPaymentMessage()).contains("TXN_");
        assertThat(completed.getWalletBalance()).isLessThan(1000);
        assertThat(completed.getTimeline()).extracting("title")
                .contains("Route updated", "Wallet paid automatically", "Journey continues");
    }

    @Test
    void plansDelhiMumbaiWithFourStopsInUnderTenSeconds() {
        long corridorUserId = 502L;
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .userId(corridorUserId)
                .makeAndModel("Vidyut Long Range Demo")
                .registrationNumber("DL01EV0502")
                .batteryCapacity("70.5 kWh")
                .connectorType("CCS2")
                .build());

        addStation("Delhi NH48 Start", 28.4595, 77.0266, 15.2, 180, 1);
        addStation("Jaipur NH48", 26.8870, 75.7050, 13.8, 180, 1);
        addStation("Kishangarh Corridor", 26.5906, 74.8564, 12.9, 180, 0);
        addStation("Udaipur Gateway", 24.6500, 73.7100, 14.0, 240, 1);
        addStation("Ahmedabad Ring", 23.0700, 72.5000, 13.5, 240, 2);
        addStation("Vadodara Express", 22.3072, 73.1812, 12.7, 180, 0);
        addStation("Surat NH48", 21.2200, 72.9600, 13.2, 240, 1);
        addStation("Mumbai Arrival", 19.2183, 72.9781, 16.0, 240, 2);

        AutopilotTripRequest request = AutopilotTripRequest.builder()
                .vehicleId(vehicle.getId()).origin("Delhi").destination("Mumbai")
                .currentBatteryPercent(80).minimumArrivalBatteryPercent(15)
                .maximumChargingBudget(2200).optimizeFor("TIME")
                .tripPurpose("REST_STOP").arrivalDeadline("23:30")
                .idempotencyKey("DELHI-MUMBAI-502").build();

        Instant started = Instant.now();
        var proposal = autopilotService.previewTrip(corridorUserId, request);

        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(10));
        assertThat(proposal.getStops()).hasSize(4);
        assertThat(proposal.getStops()).allSatisfy(stop -> {
            assertThat(stop.getTimingScore()).isIn("HIGH", "MEDIUM", "LOW");
            assertThat(stop.getPredictedSlotFreeAt()).isNotNull();
        });
    }

    private void addStation(String name, double latitude, double longitude, double price, double power, int queue) {
        ChargingStation station = ChargingStation.builder()
                .name(name)
                .address(name + " address")
                .city(name)
                .latitude(latitude)
                .longitude(longitude)
                .pricePerKwh(price)
                .rating(4.8)
                .reviewCount(100)
                .queueCount(queue)
                .occupancyPercent(queue * 12)
                .status(StationStatus.ACTIVE)
                .availability(StationAvailability.AVAILABLE)
                .connectors(new ArrayList<>())
                .build();
        station.getConnectors().add(ChargingConnector.builder()
                .station(station)
                .type(ConnectorType.CCS2)
                .powerKw(power)
                .available(true)
                .chargerCode("TEST-" + name.replace(" ", "-").toUpperCase())
                .status(ChargerStatus.ONLINE)
                .build());
        stationRepository.save(station);
    }
}
