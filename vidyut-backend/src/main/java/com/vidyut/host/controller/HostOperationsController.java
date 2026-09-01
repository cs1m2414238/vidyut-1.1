package com.vidyut.host.controller;

import com.vidyut.booking.dto.BookingStatusUpdateRequest;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.host.dto.*;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.service.HostOperationsService;
import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.entity.Payout;
import com.vidyut.station.dto.StationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/host")
@RequiredArgsConstructor
public class HostOperationsController {
    private final HostOperationsService hostService;
    private final NotificationService notificationService;
    private final CurrentUserUtil currentUser;

    @GetMapping("/profile") public ResponseEntity<ApiResponse<HostProfileResponse>> profile() { return ok(hostService.profile(id())); }
    @PutMapping("/profile") public ResponseEntity<ApiResponse<HostProfileResponse>> updateProfile(@Valid @RequestBody HostProfileUpdateRequest request) { return ok(hostService.updateProfile(id(), request)); }
    @PostMapping("/verification") public ResponseEntity<ApiResponse<HostProfileResponse>> verification(@Valid @RequestBody HostVerificationRequest request) { return ok(hostService.submitVerification(id(), request)); }
    @PutMapping("/bank") public ResponseEntity<ApiResponse<HostProfileResponse>> bank(@Valid @RequestBody HostBankRequest request) { return ok(hostService.updateBank(id(), request)); }
    @PutMapping("/settings") public ResponseEntity<ApiResponse<HostProfileResponse>> settings(@RequestBody HostSettingsRequest request) { return ok(hostService.updateSettings(id(), request)); }
    @PostMapping("/email-verification/request") public ResponseEntity<ApiResponse<String>> requestEmailCode() { return ok(hostService.requestEmailCode(id())); }
    @PostMapping("/email-verification/confirm") public ResponseEntity<ApiResponse<HostProfileResponse>> confirmEmailCode(@Valid @RequestBody HostEmailVerificationRequest request) { return ok(hostService.confirmEmailCode(id(), request.getCode())); }

    @GetMapping("/dashboard") public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() { return ok(hostService.dashboard(id())); }
    @PutMapping("/stations/{stationId}/availability") public ResponseEntity<ApiResponse<StationResponse>> availability(@PathVariable Long stationId, @Valid @RequestBody HostAvailabilityRequest request) { return ok(hostService.updateAvailability(id(), stationId, request)); }
    @GetMapping("/monitoring") public ResponseEntity<ApiResponse<List<Map<String, Object>>>> monitoring() { return ok(hostService.monitoring(id())); }
    @GetMapping("/connectors/{connectorId}/maintenance-impact") public ResponseEntity<ApiResponse<Map<String, Object>>> maintenanceImpact(@PathVariable Long connectorId) { return ok(hostService.maintenanceImpact(id(), connectorId)); }
    @PutMapping("/connectors/{connectorId}/status") public ResponseEntity<ApiResponse<Map<String, Object>>> chargerStatus(@PathVariable Long connectorId, @Valid @RequestBody HostChargerStatusRequest request) { return ok(hostService.updateChargerStatus(id(), connectorId, request)); }

    @GetMapping("/bookings") public ResponseEntity<ApiResponse<List<HostBookingResponse>>> bookings() { return ok(hostService.bookings(id())); }
    @PatchMapping("/bookings/{bookingId}/status") public ResponseEntity<ApiResponse<HostBookingResponse>> bookingStatus(@PathVariable Long bookingId, @Valid @RequestBody BookingStatusUpdateRequest request) { return ok(hostService.updateBooking(id(), bookingId, request.getStatus())); }
    @PatchMapping("/bookings/{bookingId}/reschedule") public ResponseEntity<ApiResponse<HostBookingResponse>> reschedule(@PathVariable Long bookingId, @Valid @RequestBody HostRescheduleRequest request) { return ok(hostService.reschedule(id(), bookingId, request.getStartTime())); }

    @GetMapping("/earnings") public ResponseEntity<ApiResponse<Map<String, Object>>> earnings() { return ok(hostService.earnings(id())); }
    @PostMapping("/payouts/withdraw") public ResponseEntity<ApiResponse<Payout>> withdraw(@Valid @RequestBody HostWithdrawRequest request) { return ok(hostService.withdraw(id(), request.getAmount())); }
    @GetMapping("/reviews") public ResponseEntity<ApiResponse<List<HostReview>>> reviews() { return ok(hostService.reviews(id())); }
    @PatchMapping("/reviews/{reviewId}/reply") public ResponseEntity<ApiResponse<HostReview>> reply(@PathVariable Long reviewId, @Valid @RequestBody HostReviewActionRequest request) { return ok(hostService.replyReview(id(), reviewId, request.getMessage())); }
    @PatchMapping("/reviews/{reviewId}/report") public ResponseEntity<ApiResponse<HostReview>> report(@PathVariable Long reviewId, @Valid @RequestBody HostReviewActionRequest request) { return ok(hostService.reportReview(id(), reviewId, request.getMessage())); }
    @PostMapping("/ai/ask") public ResponseEntity<ApiResponse<Map<String, Object>>> assistant(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody HostAiRequest request) {
        return ok(hostService.assistant(id(), request.getQuestion(), authorization));
    }
    @PostMapping("/ai/context") public ResponseEntity<ApiResponse<Map<String, Object>>> assistantContext(
            @Valid @RequestBody HostAiRequest request) {
        return ok(hostService.assistant(id(), request.getQuestion()));
    }
    @GetMapping("/ai/readiness") public ResponseEntity<ApiResponse<Map<String, Object>>> readiness() {
        return ok(hostService.evaluatePropertyReadiness(id()));
    }
    @GetMapping("/ai/offers") public ResponseEntity<ApiResponse<Map<String, Object>>> offers(@RequestParam(required = false) String property) {
        return ok(hostService.compareCompanyOffers(id(), property));
    }
    @GetMapping("/ai/charger-health") public ResponseEntity<ApiResponse<Map<String, Object>>> chargerHealth() {
        return ok(hostService.getHostedChargerHealth(id()));
    }
    @GetMapping("/ai/property-duplicate") public ResponseEntity<ApiResponse<Map<String, Object>>> propertyDuplicate(
            @RequestParam String title, @RequestParam String address, @RequestParam String city) {
        return ok(hostService.checkPropertyDuplicate(id(), title, address, city));
    }
    @PostMapping("/ai/prepare-property-draft") public ResponseEntity<ApiResponse<Map<String, Object>>> preparePropertyDraft(
            @Valid @RequestBody com.vidyut.land.dto.LandListingCreateRequest request) {
        return ok(hostService.preparePropertyListing(id(), request));
    }
    @PostMapping("/ai/create-property-draft") public ResponseEntity<ApiResponse<Map<String, Object>>> createPropertyDraft(
            @RequestParam(defaultValue = "false") boolean approved,
            @Valid @RequestBody com.vidyut.land.dto.LandListingCreateRequest request) {
        if (!approved) throw new BadRequestException("Host approval is required before creating a property draft");
        return ok(hostService.createPropertyDraft(id(), request));
    }
    @PostMapping("/ai/actions") public ResponseEntity<ApiResponse<Map<String, Object>>> agentAction(@Valid @RequestBody HostAgentActionRequest request) { return ok(hostService.executeAgentAction(id(), request)); }
    @GetMapping("/notifications") public ResponseEntity<ApiResponse<List<Notification>>> notifications() { return ok(notificationService.getNotificationsForUser(id())); }
    @PatchMapping("/notifications/{notificationId}/read") public ResponseEntity<ApiResponse<Notification>> markNotificationRead(@PathVariable Long notificationId) { return ok(notificationService.markRead(id(), notificationId)); }
    @PatchMapping("/notifications/read-all") public ResponseEntity<ApiResponse<Void>> markAllNotificationsRead() {
        notificationService.markAllRead(id());
        return ResponseEntity.ok(ApiResponse.success("Notifications marked as read", null));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> report(@RequestParam(defaultValue = "EARNINGS") String type, @RequestParam(defaultValue = "PDF") String format) {
        String normalized = format.equalsIgnoreCase("PDF") ? "PDF" : "XLSX";
        byte[] data = hostService.exportReport(id(), type, normalized);
        MediaType contentType = normalized.equals("PDF") ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=vidyut-host-" + type.toLowerCase(Locale.ROOT) + "." + normalized.toLowerCase(Locale.ROOT)).body(data);
    }

    private Long id() { return currentUser.getCurrentAccountId(); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T data) { return ResponseEntity.ok(ApiResponse.success(data)); }
}
