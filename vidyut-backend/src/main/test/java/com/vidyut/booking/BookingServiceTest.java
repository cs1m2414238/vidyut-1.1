package com.vidyut.booking;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.service.ChargingStationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.vidyut.common.exception.DuplicateResourceException;
import java.time.LocalDateTime;

@SpringBootTest
public class BookingServiceTest {

    @Autowired
    private ChargingStationService stationService;

    @Autowired
    private BookingService bookingService;

    @Test
    public void testCreateBooking() {
        StationCreateRequest sReq = StationCreateRequest.builder()
                .name("Hazratganj Station")
                .address("Hazratganj, Lucknow")
                .pricePerKwh(15.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(22.0)
                .bookingSlotMinutes(15)
                .build();
        StationResponse station = stationService.createStation(sReq, 1L);

        BookingCreateRequest bReq = BookingCreateRequest.builder()
                .stationId(station.getId())
                .durationHours(2)
                .build();

        BookingResponse booking = bookingService.createBooking(bReq, 101L);
        assertNotNull(booking.getId());
        assertEquals("Hazratganj Station", booking.getStationName());
        assertTrue(booking.getTotalAmount() > 0);
        assertEquals(120, booking.getDurationMinutes());
    }

    @Test
    void unreadCountAndSeenStateAreScopedToTheBookingOwner() {
        StationCreateRequest stationRequest = StationCreateRequest.builder()
                .name("Unread Count Station")
                .address("Gomti Nagar, Lucknow")
                .pricePerKwh(12.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(22.0)
                .bookingSlotMinutes(15)
                .build();
        StationResponse station = stationService.createStation(stationRequest, 1L);
        long accountId = 88001L;

        BookingResponse booking = bookingService.createBooking(BookingCreateRequest.builder()
                .stationId(station.getId())
                .durationMinutes(45)
                .build(), accountId);

        assertEquals(45, booking.getDurationMinutes());
        assertEquals(1, bookingService.getUnreadActiveCount(accountId));
        assertEquals(0, bookingService.getUnreadActiveCount(88002L));

        bookingService.markBookingsSeen(accountId);
        assertEquals(0, bookingService.getUnreadActiveCount(accountId));
    }

    @Test
    void connectorCapacityPreventsOverlappingDoubleBooking() {
        StationResponse station = stationService.createStation(StationCreateRequest.builder()
                .name("Race Protected Station").address("Aliganj, Lucknow").pricePerKwh(14.0)
                .connectorType(ConnectorType.CCS2).powerKw(30).bookingSlotMinutes(30).build(), 1L);
        LocalDateTime start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        BookingCreateRequest request = BookingCreateRequest.builder().stationId(station.getId())
                .startTime(start).durationMinutes(60).build();

        bookingService.createBooking(request, 8101L);
        assertThatThrownBy(() -> bookingService.createBooking(request, 8102L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("slot is full");
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheOriginalBooking() {
        StationResponse station = stationService.createStation(StationCreateRequest.builder()
                .name("Idempotent Station").address("Indira Nagar, Lucknow").pricePerKwh(11.0)
                .connectorType(ConnectorType.CCS2).powerKw(22).bookingSlotMinutes(30).build(), 1L);
        BookingCreateRequest request = BookingCreateRequest.builder().stationId(station.getId())
                .startTime(LocalDateTime.now().plusDays(3)).durationMinutes(60)
                .idempotencyKey("checkout-8301").build();

        BookingResponse first = bookingService.createBooking(request, 8301L);
        BookingResponse repeated = bookingService.createBooking(request, 8301L);
        assertEquals(first.getId(), repeated.getId());
        assertEquals("checkout-8301", repeated.getIdempotencyKey());
    }
}
