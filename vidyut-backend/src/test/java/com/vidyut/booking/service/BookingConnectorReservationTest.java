package com.vidyut.booking.service;
import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.outlet.service.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.admin.service.OperationalControlService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness=Strictness.LENIENT)
class BookingConnectorReservationTest {
    @Mock BookingRepository bookingRepository;
    @Mock ChargingStationRepository stationRepository;
    @Mock OutletAccessService outletAccessService;
    @Mock OperationalControlService operationalControlService;
    @Mock NotificationService notificationService;
    @InjectMocks BookingServiceImpl service;
    BookingCreateRequest request;

    @BeforeEach void setup() {
        var station=ChargingStation.builder().id(1L).pricePerKwh(12).name("Test station").connectors(List.of(
                ChargingConnector.builder().id(11L).status(ChargerStatus.ONLINE).available(true).powerKw(50).build(),
                ChargingConnector.builder().id(12L).status(ChargerStatus.ONLINE).available(true).powerKw(150).build())).build();
        when(stationRepository.findLockedById(1L)).thenReturn(Optional.of(station));
        when(outletAccessService.resolveRate(2L,1L,12)).thenReturn(new OutletRateDecision(false,null,null,12,12));
        when(bookingRepository.save(any())).thenAnswer(inv->{Booking b=inv.getArgument(0);b.setId(90L);return b;});
        request=BookingCreateRequest.builder().stationId(1L).connectorId(11L).durationHours(1).startTime(LocalDateTime.now().plusHours(1)).build();
    }
    @Test void exactConnectorIsPersistedAndDoesNotUseSiblingsPower() {
        var result=service.createBooking(request,2L);
        assertThat(result.getConnectorId()).isEqualTo(11L);
        assertThat(result.getKwhDelivered()).isEqualTo(50);
        verify(bookingRepository).save(argThat(b->b.getConnectorId().equals(11L)));
    }
    @Test void healthySecondConnectorDoesNotAllowDoubleBookingTheSelectedOne() {
        when(bookingRepository.countConnectorOverlapping(eq(11L),any(),any(),any())).thenReturn(1L);
        assertThatThrownBy(()->service.createBooking(request,2L)).hasMessageContaining("exact recovery connector is already reserved");
        verify(bookingRepository,never()).save(any());
    }
    @Test void connectorMustBelongToTheSelectedStation() {
        request.setConnectorId(999L);
        assertThatThrownBy(()->service.createBooking(request,2L)).hasMessageContaining("no available charging connector");
        verify(bookingRepository,never()).save(any());
    }
}
