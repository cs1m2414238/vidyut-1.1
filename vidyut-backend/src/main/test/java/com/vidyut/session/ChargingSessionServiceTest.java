package com.vidyut.session;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.service.BookingService;
import com.vidyut.session.entity.ChargingSessionStatus;
import com.vidyut.session.service.ChargingSessionService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.service.ChargingStationService;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChargingSessionServiceTest {
    @Autowired private ChargingSessionService sessionService;
    @Autowired private BookingService bookingService;
    @Autowired private ChargingStationService stationService;
    @Autowired private VehicleRepository vehicleRepository;

    @Test
    void startingAndStoppingSessionUpdatesBookingAndVehicleState() {
        long userId = 9201L;
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(userId).makeAndModel("Session EV")
                .registrationNumber("UP32SS9201").batteryCapacity("40 kWh").connectorType("CCS2")
                .batteryPercent(35).btSessionControlEnabled(true).btSimulatorEnabled(true).build());
        var station = stationService.createStation(StationCreateRequest.builder().name("Session Station")
                .address("Gomti Nagar").pricePerKwh(12.0).connectorType(ConnectorType.CCS2)
                .powerKw(30).bookingSlotMinutes(30).build(), 55L);
        var booking = bookingService.createBooking(BookingCreateRequest.builder().stationId(station.getId())
                .vehicleId(vehicle.getId()).durationMinutes(60).build(), userId);

        var active = sessionService.start(userId, booking.getId());
        assertThat(active.getStatus()).isEqualTo(ChargingSessionStatus.ACTIVE);
        assertThat(bookingService.getBookingById(booking.getId(), userId).getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);

        var synced = sessionService.updateSoc(userId, active.getId(), 62, true);
        assertThat(synced.getCurrentBatteryPercent()).isEqualTo(62);
        assertThat(synced.getTelemetrySource()).isEqualTo("BLUETOOTH_DEMO");
        assertThat(sessionService.control(userId, active.getId(), "START").getStatus())
                .isEqualTo(ChargingSessionStatus.ACTIVE);

        var completed = sessionService.stop(userId, active.getId());
        assertThat(completed.getStatus()).isEqualTo(ChargingSessionStatus.COMPLETED);
        assertThat(bookingService.getBookingById(booking.getId(), userId).getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getLastChargingStation())
                .isEqualTo("Session Station");
    }
}
