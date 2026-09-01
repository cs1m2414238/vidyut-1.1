package com.vidyut.autopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.dto.*;
import com.vidyut.autopilot.entity.*;
import com.vidyut.autopilot.repository.*;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.*;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.*;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.service.WalletService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness=Strictness.LENIENT)
class AgentRecoveryWorkflowTest {
    @Mock AutopilotTripRepository tripRepository;
    @Mock AutopilotStopRepository stopRepository;
    @Mock AutopilotActionRepository actionRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock ChargingStationRepository stationRepository;
    @Mock ChargingConnectorRepository recoveryConnectors;
    @Mock BookingService bookingService;
    @Mock WalletService walletService;
    @Mock SafeRecoveryPlanner recoveryPlanner;
    @Mock AutopilotPositionService positions;
    @Spy RecoveryStore recoveryStore=new RecoveryStore(new ObjectMapper().findAndRegisterModules());
    @InjectMocks AutopilotService service;
    AutopilotTrip trip;
    AutopilotStop failed;
    RecoverySession session;
    RecoveryPlan plan;
    List<AutopilotStop> persisted;

    @BeforeEach void setup() {
        trip=AutopilotTrip.builder().id(1L).userId(2L).vehicleId(3L).status(AutopilotTripStatus.MONITORING)
                .autonomyMode("ASK_BEFORE_ACTIONS").currentBatteryPercent(39).minimumArrivalBatteryPercent(15).totalDistanceKm(500).build();
        failed=AutopilotStop.builder().id(10L).tripId(1L).stationId(20L).connectorId(21L).stationName("Failed station")
                .connectorType("CCS2").bookingId(30L).sequenceNumber(1).status(AutopilotStopStatus.RESERVED).build();
        persisted=new ArrayList<>(List.of(failed));
        var replacement=AutopilotStop.builder().tripId(1L).stationId(40L).connectorId(41L).stationName("Bridge")
                .status(AutopilotStopStatus.PLANNED).connectorType("CCS2").sequenceNumber(1).chargingMinutes(10).targetBatteryPercent(49).build();
        var route=new OsrmRoute(142000,8520,new OsrmGeometry("LineString",List.of(List.of(77.0,28.0),List.of(77.0,26.0))),
                List.of(new OsrmLeg(22000,1320),new OsrmLeg(120000,7200)));
        plan=new RecoveryPlan("plan-1","BRIDGE_RECOVERY",List.of(replacement),route,OsrmClient.RouteEngine.PRIMARY,18,
                142,10,0,4,125,160.0,180,150,3.74);
        session=RecoverySession.builder().incidentId("incident-1").failedStopId(10L).originalTripStatus(trip.getStatus())
                .plans(List.of(plan)).evidence(AutopilotRecoveryResponse.builder().state("CANDIDATES_READY").incidentId("incident-1")
                        .capturedAt(AutopilotPositionService.now()).currentSoc(39).reserveSoc(15).build()).build();
        recoveryStore.write(trip,session);
        when(tripRepository.findOwnedForUpdate(1L,2L)).thenReturn(Optional.of(trip));
        when(tripRepository.findByIdAndUserId(1L,2L)).thenReturn(Optional.of(trip));
        when(stopRepository.findById(10L)).thenReturn(Optional.of(failed));
        when(stopRepository.findByTripIdOrderBySequenceNumberAscIdAsc(1L)).thenAnswer(inv->new ArrayList<>(persisted));
        when(stopRepository.save(any())).thenAnswer(inv->{AutopilotStop s=inv.getArgument(0);if(s.getId()==null){s.setId(11L);persisted.add(s);}return s;});
        when(vehicleRepository.findByIdAndUserId(3L,2L)).thenReturn(Optional.of(Vehicle.builder().batteryCapacity("66.5").efficiencyWhPerKm(170.0).build()));
        when(walletService.getWalletByUserId(2L)).thenReturn(WalletResponse.builder().balance(5000).build());
        when(recoveryPlanner.revalidate(any(),any(),any(),any(),any(),any())).thenAnswer(inv->inv.getArgument(4));
        when(recoveryConnectors.findByIdForUpdate(41L)).thenReturn(Optional.of(ChargingConnector.builder().id(41L).available(true).status(ChargerStatus.ONLINE).build()));
        when(bookingService.createBooking(any(),eq(2L))).thenReturn(BookingResponse.builder().id(50L).build());
    }

    @Test void askPreparesWithoutCancellingBookingOrApplyingNavigation() {
        var response=service.prepareSafeReroute(1L,2L,"incident-1","plan-1","GEMINI");
        assertThat(response.getRecovery().getState()).isEqualTo("AWAITING_APPROVAL");
        assertThat(response.getRecovery().getAdditionalDistanceKm()).isEqualTo(-18);
        assertThat(response.getRecovery().getAdditionalMinutes()).isEqualTo(-24);
        assertThat(trip.getTotalDistanceKm()).isEqualTo(500);
        assertThat(failed.getStatus()).isEqualTo(AutopilotStopStatus.RESERVED);
        verifyNoInteractions(bookingService,positions);
        verify(stopRepository,never()).save(any());
        assertThatThrownBy(()->service.executeAgentReroute(1L,2L,"incident-1","plan-1")).hasMessageContaining("does not permit automatic");
        verifyNoInteractions(bookingService);
    }

    @Test void approvalValidatesBeforeMutationsAndReservesExactConnectorOnce() {
        service.prepareSafeReroute(1L,2L,"incident-1","plan-1","GEMINI");
        clearInvocations(recoveryPlanner);
        var result=service.approvePreparedReroute(1L,2L,"incident-1","plan-1");
        assertThat(result.getRecovery().getState()).isEqualTo("EXECUTED");
        InOrder order=inOrder(recoveryPlanner,bookingService,positions);
        order.verify(recoveryPlanner).revalidate(any(),any(),any(),any(),any(),any());
        order.verify(bookingService).cancelBookingWithoutFee(eq(30L),eq(2L),anyString());
        order.verify(bookingService).createBooking(argThat(r->r.getConnectorId().equals(41L)),eq(2L));
        order.verify(positions).setNavigation(any(),any());
        service.approvePreparedReroute(1L,2L,"incident-1","plan-1");
        verify(bookingService,times(1)).createBooking(any(),eq(2L));
    }

    @Test void recommendOnlyNeverExecutesEvenViaApprovalEndpoint() {
        trip.setAutonomyMode("RECOMMEND_ONLY");
        var result=service.prepareSafeReroute(1L,2L,"incident-1","plan-1","AGENT_POLICY");
        assertThat(result.getRecovery().getState()).isEqualTo("SUGGESTED");
        assertThat(trip.getStatus()).isEqualTo(AutopilotTripStatus.MONITORING);
        assertThatThrownBy(()->service.approvePreparedReroute(1L,2L,"incident-1","plan-1")).hasMessageContaining("does not permit");
        verifyNoInteractions(bookingService,positions);
    }

    @Test void fullAutopilotMayExecutePreparedSafePlan() {
        trip.setAutonomyMode("FULL_AUTOPILOT");
        assertThat(service.prepareSafeReroute(1L,2L,"incident-1","plan-1","GEMINI").getRecovery().getState()).isEqualTo("PREPARED");
        assertThat(service.executeAgentReroute(1L,2L,"incident-1","plan-1").getRecovery().getState()).isEqualTo("EXECUTED");
    }

    @Test void freshSafetyFailureRejectsApprovalBeforeAnyBookingChange() {
        service.prepareSafeReroute(1L,2L,"incident-1","plan-1","GEMINI");
        when(recoveryPlanner.revalidate(any(),any(),any(),any(),any(),any())).thenThrow(new BadRequestException("NO_SAFE_RECOVERY_ROUTE"));
        assertThatThrownBy(()->service.approvePreparedReroute(1L,2L,"incident-1","plan-1")).hasMessageContaining("NO_SAFE");
        verifyNoInteractions(bookingService,positions);
        assertThat(failed.getStatus()).isEqualTo(AutopilotStopStatus.RESERVED);
    }

    @Test void oldApprovalCannotApplyAChangedProposal() {
        service.prepareSafeReroute(1L,2L,"incident-1","plan-1","GEMINI");
        assertThatThrownBy(()->service.approvePreparedReroute(1L,2L,"incident-1","other-plan")).hasMessageContaining("proposal has changed");
        verifyNoInteractions(bookingService);
    }

    @Test void driverIncidentOnlyRecordsEventAndKeepsFailedBooking() {
        trip.setRecoveryJson(null);
        var result=service.simulateChargerFault(1L,2L,10L);
        assertThat(result.getRecovery().getState()).isEqualTo("INCIDENT_DETECTED");
        assertThat(failed.getStatus()).isEqualTo(AutopilotStopStatus.RESERVED);
        verifyNoInteractions(bookingService,recoveryPlanner,positions);
    }

    @Test void crossAccountCannotReadOrExecuteRecovery() {
        assertThatThrownBy(()->service.recoveryContext(1L,999L,"incident-1")).hasMessageContaining("not found");
        assertThatThrownBy(()->service.executeAgentReroute(1L,999L,"incident-1","plan-1")).hasMessageContaining("not found");
        verifyNoInteractions(bookingService);
    }

    @Test void reservationFailureRollsBackCancellationInTheSpringTransaction() {
        service.prepareSafeReroute(1L,2L,"incident-1","plan-1","GEMINI");
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:recovery_atomic;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.execute("create table recovery_booking_test(id bigint primary key,status varchar(30))");
        jdbc.update("insert into recovery_booking_test values (30,'CONFIRMED')");
        doAnswer(inv->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("update recovery_booking_test set status='CANCELLED' where id=30"); return null;
        }).when(bookingService).cancelBookingWithoutFee(eq(30L),eq(2L),anyString());
        when(bookingService.createBooking(any(),eq(2L))).thenAnswer(inv->{
            jdbc.update("insert into recovery_booking_test values (50,'CONFIRMED')");
            throw new BadRequestException("Reservation conflict at downstream connector");
        });
        var factory=new org.springframework.aop.framework.ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(source),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy=(AutopilotService)factory.getProxy();
        assertThatThrownBy(()->proxy.approvePreparedReroute(1L,2L,"incident-1","plan-1")).hasMessageContaining("Reservation conflict");
        assertThat(jdbc.queryForObject("select status from recovery_booking_test where id=30",String.class)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from recovery_booking_test",Integer.class)).isEqualTo(1);
        verifyNoInteractions(positions);
    }
}
