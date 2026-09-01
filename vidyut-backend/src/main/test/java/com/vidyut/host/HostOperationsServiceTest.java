package com.vidyut.host;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.*;
import com.vidyut.booking.entity.*;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.*;
import com.vidyut.host.dto.*;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.repository.HostReviewRepository;
import com.vidyut.host.service.HostOperationsService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class HostOperationsServiceTest {
    @Autowired HostOperationsService hostService;
    @Autowired AccountRepository accountRepository;
    @Autowired HostProfileRepository hostProfileRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired HostReviewRepository reviewRepository;

    @Test
    void verifiedHostCanOperateEarnWithdrawReviewAndExport() {
        Account host = saveHost("host-ops@test.local", true, true, true);
        Account customer = saveEv("driver@test.local");
        var station = hostService.createStation(host.getId(), stationRequest("Home charger"));
        bookingRepository.save(Booking.builder().userId(customer.getId()).stationId(station.getId())
                .stationName(station.getName()).stationAddress(station.getAddress()).startTime(LocalDateTime.now())
                .durationHours(2).totalAmount(480).kwhDelivered(24).status(BookingStatus.COMPLETED).build());
        HostReview review = reviewRepository.save(HostReview.builder().hostAccountId(host.getId()).stationId(station.getId())
                .customerAccountId(customer.getId()).customerName("EV Driver").rating(5).comment("Great private charger").build());

        HostAvailabilityRequest availability = new HostAvailabilityRequest();
        availability.setAvailability(StationAvailability.AVAILABLE);
        availability.setWeeklySchedule("MON-SUN 06:00-23:00");
        availability.setHolidaySchedule("2026-08-15 CLOSED");
        availability.setBookingSlotMinutes(60);
        availability.setAutoAvailability(true);
        hostService.updateAvailability(host.getId(), station.getId(), availability);

        assertThat(hostService.dashboard(host.getId())).containsEntry("totalChargers", 1);
        assertThat(hostService.bookings(host.getId())).hasSize(1).first().extracting(HostBookingResponse::getCustomerEmail).isEqualTo(customer.getEmail());
        assertThat(hostService.earnings(host.getId())).containsEntry("lifetime", 480.0).containsEntry("availableBalance", 480.0);
        assertThat(hostService.withdraw(host.getId(), 200).getStatus()).isEqualTo("PENDING");
        assertThat(hostService.replyReview(host.getId(), review.getId(), "Thank you").getHostReply()).isEqualTo("Thank you");
        assertThat(hostService.reportReview(host.getId(), review.getId(), "Abuse review").isReported()).isTrue();
        assertThat(hostService.assistant(host.getId(), "How can I increase my earnings?").get("answer").toString()).contains("availability");
        assertThat(hostService.assistant(host.getId(), "What facilities should I upgrade?").get("answer").toString()).contains("lighting");
        assertThat(hostService.assistant(host.getId(), "When should I keep my charger open?").get("answer").toString()).contains("Keep the charger open");
        assertThat(new String(hostService.exportReport(host.getId(), "EARNINGS", "PDF"), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(new String(hostService.exportReport(host.getId(), "USAGE", "XLSX"), 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
    }

    @Test
    void pendingHostCannotRegisterCharger() {
        Account host = saveHost("pending-host@test.local", false, false, false);
        assertThatThrownBy(() -> hostService.createStation(host.getId(), stationRequest("Blocked")))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("email");
    }

    @Test
    void hostCannotAccessAnotherHostsResources() {
        Account owner = saveHost("owner-host@test.local", true, true, true);
        Account attacker = saveHost("other-host@test.local", true, true, true);
        var station = hostService.createStation(owner.getId(), stationRequest("Owner charger"));
        HostAvailabilityRequest request = new HostAvailabilityRequest();
        request.setAvailability(StationAvailability.UNAVAILABLE);
        request.setBookingSlotMinutes(60);
        assertThatThrownBy(() -> hostService.updateAvailability(attacker.getId(), station.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void hostBookingStatusMustFollowTheOperationalLifecycle() {
        Account host = saveHost("status-host@test.local", true, true, true);
        Account customer = saveEv("status-driver@test.local");
        var station = hostService.createStation(host.getId(), stationRequest("Status charger"));
        Booking booking = bookingRepository.save(Booking.builder().userId(customer.getId()).stationId(station.getId())
                .stationName(station.getName()).stationAddress(station.getAddress()).startTime(LocalDateTime.now())
                .durationHours(1).totalAmount(200).status(BookingStatus.PENDING).build());

        assertThat(hostService.updateBooking(host.getId(), booking.getId(), BookingStatus.CONFIRMED).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(hostService.updateBooking(host.getId(), booking.getId(), BookingStatus.CONFIRMED).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(hostService.updateBooking(host.getId(), booking.getId(), BookingStatus.IN_PROGRESS).getStatus())
                .isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(hostService.updateBooking(host.getId(), booking.getId(), BookingStatus.COMPLETED).getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
        assertThatThrownBy(() -> hostService.updateBooking(host.getId(), booking.getId(), BookingStatus.CONFIRMED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("COMPLETED to CONFIRMED");
    }

    private Account saveHost(String email, boolean emailVerified, boolean verified, boolean bankVerified) {
        Account account = accountRepository.save(Account.builder().email(email).passwordHash("hash")
                .accountType(AccountType.INDIVIDUAL).roles(Set.of(AccountRole.ROLE_HOST)).emailVerified(emailVerified).enabled(true).build());
        hostProfileRepository.save(HostProfile.builder().account(account).displayName("Test Host").verified(verified)
                .verificationStatus(verified ? HostVerificationStatus.VERIFIED : HostVerificationStatus.PENDING)
                .bankVerified(bankVerified).bankAccountLast4(bankVerified ? "1234" : null).build());
        return account;
    }

    private Account saveEv(String email) {
        return accountRepository.save(Account.builder().email(email).passwordHash("hash").accountType(AccountType.INDIVIDUAL)
                .roles(Set.of(AccountRole.ROLE_EV_USER)).emailVerified(true).enabled(true).build());
    }

    private StationCreateRequest stationRequest(String name) {
        return StationCreateRequest.builder().name(name).address("1 Private Lane").city("Lucknow")
                .latitude(26.84).longitude(80.94).pricePerKwh(16.0).connectorType(ConnectorType.TYPE2).powerKw(7.4)
                .workingHours("06:00-23:00").weeklySchedule("MON-SUN 06:00-23:00").bookingSlotMinutes(60).build();
    }
}
