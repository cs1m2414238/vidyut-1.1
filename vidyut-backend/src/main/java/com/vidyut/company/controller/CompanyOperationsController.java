package com.vidyut.company.controller;

import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.dto.BookingStatusUpdateRequest;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.company.dto.*;
import com.vidyut.company.entity.CompanyEmployee;
import com.vidyut.company.entity.CompanyActivityLog;
import com.vidyut.company.service.CompanyOperationsService;
import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.payment.entity.Payout;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.dto.StationUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyOperationsController {
    private final CompanyOperationsService operationsService;
    private final NotificationService notificationService;
    private final CurrentUserUtil currentUser;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.dashboard(accountId())));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analytics() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.analytics(accountId())));
    }

    @GetMapping("/network")
    public ResponseEntity<ApiResponse<CompanyNetworkResponse>> network() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.network(accountId())));
    }

    @GetMapping("/maintenance-tickets")
    public ResponseEntity<ApiResponse<List<MaintenanceTicketResponse>>> maintenanceTickets() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.maintenanceTickets(accountId())));
    }

    @PostMapping("/maintenance-tickets")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> createMaintenanceTicket(
            @Valid @RequestBody MaintenanceTicketCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Maintenance work order created",
                operationsService.createMaintenanceTicket(accountId(), request)));
    }

    @PatchMapping("/maintenance-tickets/{id}")
    public ResponseEntity<ApiResponse<MaintenanceTicketResponse>> updateMaintenanceTicket(@PathVariable Long id,
            @Valid @RequestBody MaintenanceTicketUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Maintenance work order updated",
                operationsService.updateMaintenanceTicket(accountId(), id, request)));
    }

    @GetMapping("/stations")
    public ResponseEntity<ApiResponse<List<StationResponse>>> stations(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(operationsService.getStations(accountId(), q)));
    }

    @PostMapping("/stations")
    public ResponseEntity<ApiResponse<StationResponse>> createStation(@Valid @RequestBody StationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Station created", operationsService.createStation(accountId(), request)));
    }

    @PutMapping("/stations/{id}")
    public ResponseEntity<ApiResponse<StationResponse>> updateStation(@PathVariable Long id,
                                                                       @RequestBody StationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Station updated", operationsService.updateStation(accountId(), id, request)));
    }

    @DeleteMapping("/stations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStation(@PathVariable Long id) {
        operationsService.deleteStation(accountId(), id);
        return ResponseEntity.ok(ApiResponse.success("Station deleted", null));
    }

    @GetMapping("/chargers")
    public ResponseEntity<ApiResponse<List<ChargerResponse>>> chargers(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(operationsService.getChargers(accountId(), q)));
    }

    @PostMapping("/chargers")
    public ResponseEntity<ApiResponse<ChargerResponse>> createCharger(@Valid @RequestBody ChargerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Charger added", operationsService.createCharger(accountId(), request)));
    }

    @PutMapping("/chargers/{id}")
    public ResponseEntity<ApiResponse<ChargerResponse>> updateCharger(@PathVariable Long id,
                                                                       @Valid @RequestBody ChargerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Charger updated", operationsService.updateCharger(accountId(), id, request)));
    }

    @DeleteMapping("/chargers/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCharger(@PathVariable Long id) {
        operationsService.deleteCharger(accountId(), id);
        return ResponseEntity.ok(ApiResponse.success("Charger deleted", null));
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> bookings() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.getBookings(accountId())));
    }

    @PatchMapping("/bookings/{id}/status")
    public ResponseEntity<ApiResponse<BookingResponse>> updateBookingStatus(@PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking status updated",
                operationsService.updateBookingStatus(accountId(), id, request.getStatus())));
    }

    @PutMapping("/stations/{id}/pricing")
    public ResponseEntity<ApiResponse<StationResponse>> updatePricing(@PathVariable Long id,
                                                                       @Valid @RequestBody PricingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Station pricing updated",
                operationsService.updatePricing(accountId(), id, request)));
    }

    @GetMapping("/employees")
    public ResponseEntity<ApiResponse<List<CompanyEmployee>>> employees() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.getEmployees(accountId())));
    }

    @GetMapping("/activity-logs")
    public ResponseEntity<ApiResponse<List<CompanyActivityLog>>> activityLogs() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.getActivityLogs(accountId())));
    }

    @PostMapping("/employees")
    public ResponseEntity<ApiResponse<CompanyEmployee>> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Employee added", operationsService.createEmployee(accountId(), request)));
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<ApiResponse<CompanyEmployee>> updateEmployee(@PathVariable Long id,
                                                                        @Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Employee updated", operationsService.updateEmployee(accountId(), id, request)));
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(@PathVariable Long id) {
        operationsService.deleteEmployee(accountId(), id);
        return ResponseEntity.ok(ApiResponse.success("Employee removed", null));
    }

    @PostMapping("/ai/ask")
    public ResponseEntity<ApiResponse<CompanyAgentResponse>> ask(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody AiAssistantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                operationsService.askAssistant(accountId(), request.getQuestion(), authorization)));
    }

    @PostMapping("/ai/context")
    public ResponseEntity<ApiResponse<CompanyAgentResponse>> assistantContext(
            @Valid @RequestBody AiAssistantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                operationsService.askAssistant(accountId(), request.getQuestion())));
    }

    @GetMapping("/ai/settings")
    public ResponseEntity<ApiResponse<CompanyAgentSettingsResponse>> agentSettings() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.agentSettings(accountId())));
    }

    @PutMapping("/ai/settings")
    public ResponseEntity<ApiResponse<CompanyAgentSettingsResponse>> updateAgentSettings(
            @Valid @RequestBody CompanyAgentSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company Assistant settings updated",
                operationsService.updateAgentSettings(accountId(), request)));
    }

    @PostMapping("/ai/actions")
    public ResponseEntity<ApiResponse<CompanyAgentActionResponse>> executeAgentAction(
            @Valid @RequestBody CompanyAgentActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company Assistant action processed",
                operationsService.executeAgentAction(accountId(), request)));
    }

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<Payout>>> payouts() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.payouts(accountId())));
    }

    @GetMapping("/settlements")
    public ResponseEntity<ApiResponse<CompanySettlementResponse>> settlements() {
        return ResponseEntity.ok(ApiResponse.success(operationsService.settlements(accountId())));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<Notification>>> notifications() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotificationsForUser(accountId())));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markNotificationRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(accountId(), id)));
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllNotificationsRead() {
        notificationService.markAllRead(accountId());
        return ResponseEntity.ok(ApiResponse.success("Notifications marked as read", null));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "ANALYTICS") String type,
                                         @RequestParam(defaultValue = "PDF") String format) {
        String normalized = format.equalsIgnoreCase("PDF") ? "PDF" : "XLSX";
        byte[] report = operationsService.exportReport(accountId(), type, normalized);
        MediaType mediaType = normalized.equals("PDF") ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=vidyut-" + type.toLowerCase() + "." + normalized.toLowerCase())
                .body(report);
    }

    private Long accountId() {
        return currentUser.getCurrentAccountId();
    }
}
