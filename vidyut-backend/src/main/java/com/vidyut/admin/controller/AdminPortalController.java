package com.vidyut.admin.controller;

import com.vidyut.admin.dto.*;
import com.vidyut.admin.entity.AdminAuditLog;
import com.vidyut.admin.service.AdminPortalService;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.admin.service.OperationalControlService;
import com.vidyut.admin.entity.*;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.company.dto.CompanyVerificationResponse;
import com.vidyut.company.dto.CompanyVerificationReviewRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/portal")
@RequiredArgsConstructor
public class AdminPortalController {
    private final AdminPortalService portalService;
    private final AdminControlService controlService;
    private final OperationalControlService operationalControlService;

    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<AdminPortalSnapshot>> snapshot() {
        return ResponseEntity.ok(ApiResponse.success(portalService.snapshot()));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<AdminAuditLog>>> audit() {
        return ResponseEntity.ok(ApiResponse.success(portalService.audits()));
    }

    @PatchMapping("/companies/{companyId}/review")
    public ResponseEntity<ApiResponse<CompanyVerificationResponse>> reviewCompany(
            @PathVariable Long companyId, @Valid @RequestBody CompanyVerificationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company review saved", portalService.reviewCompany(companyId, request)));
    }

    @PatchMapping("/hosts/{accountId}/review")
    public ResponseEntity<ApiResponse<Void>> reviewHost(@PathVariable Long accountId,
                                                         @Valid @RequestBody AdminReviewNoteRequest request) {
        portalService.reviewHost(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Host review saved", null));
    }

    @PatchMapping("/properties/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewProperty(@PathVariable Long id,
                                                             @Valid @RequestBody AdminReviewNoteRequest request) {
        portalService.reviewProperty(id, request);
        return ResponseEntity.ok(ApiResponse.success("Property review saved", null));
    }

    @PatchMapping("/properties/{id}/workflow")
    public ResponseEntity<ApiResponse<Map<String, Object>>> propertyWorkflow(
            @PathVariable Long id, @Valid @RequestBody PropertyWorkflowRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Property workflow updated", controlService.propertyWorkflow(id, request)));
    }

    @PatchMapping("/stations/{id}/review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewStation(
            @PathVariable Long id, @Valid @RequestBody StationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Station verification updated", controlService.reviewStation(id, request)));
    }

    @PostMapping("/incidents")
    public ResponseEntity<ApiResponse<NetworkIncident>> createIncident(@Valid @RequestBody IncidentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Incident created and affected journeys processed", controlService.createIncident(request)));
    }

    @PatchMapping("/incidents/{id}")
    public ResponseEntity<ApiResponse<NetworkIncident>> updateIncident(
            @PathVariable Long id, @Valid @RequestBody IncidentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Incident updated", controlService.updateIncident(id, request)));
    }

    @PatchMapping("/support-cases/{id}")
    public ResponseEntity<ApiResponse<AdminSupportCase>> updateSupportCase(
            @PathVariable Long id, @Valid @RequestBody SupportCaseUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Support case updated", controlService.updateSupportCase(id, request)));
    }

    @PostMapping("/green-schemes")
    public ResponseEntity<ApiResponse<AdminGreenScheme>> createGreenScheme(@Valid @RequestBody GreenSchemeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Green-finance source created", controlService.saveScheme(null, request)));
    }

    @PatchMapping("/green-schemes/{id}")
    public ResponseEntity<ApiResponse<AdminGreenScheme>> updateGreenScheme(
            @PathVariable Long id, @Valid @RequestBody GreenSchemeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Green-finance source updated", controlService.saveScheme(id, request)));
    }

    @PatchMapping("/settlements/{paymentId}")
    public ResponseEntity<ApiResponse<AdminSettlement>> updateSettlement(
            @PathVariable Long paymentId, @Valid @RequestBody SettlementStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Settlement updated", controlService.updateSettlement(paymentId, request)));
    }

    @PostMapping("/agent/query")
    public ResponseEntity<ApiResponse<AdminAgentResponse>> askAgent(@Valid @RequestBody AdminAgentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(controlService.askAgent(request)));
    }

    @PatchMapping("/products/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewProduct(@PathVariable Long id,
                                                            @Valid @RequestBody AdminReviewNoteRequest request) {
        portalService.reviewProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product review saved", null));
    }

    @PatchMapping("/accounts/{id}/enabled")
    public ResponseEntity<ApiResponse<Void>> setAccountEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        portalService.setAccountEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success("Account access updated", null));
    }

    @PatchMapping("/accounts/{id}/controls")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAccountControl(
            @PathVariable Long id, @Valid @RequestBody AccountOperationalControlRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Scoped operational control updated",
                operationalControlService.update(id, request)));
    }

    @PostMapping("/accounts/{id}/warning")
    public ResponseEntity<ApiResponse<Map<String, Object>>> warnAccount(
            @PathVariable Long id, @Valid @RequestBody AccountWarningRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Warning delivered",
                operationalControlService.sendWarning(id, request)));
    }

    @PatchMapping("/accounts/{id}/identity-access")
    public ResponseEntity<ApiResponse<Void>> emergencyIdentityAccess(
            @PathVariable Long id, @Valid @RequestBody EmergencyAccountAccessRequest request) {
        operationalControlService.emergencyIdentityAccess(id, request);
        return ResponseEntity.ok(ApiResponse.success("Emergency identity access updated", null));
    }

    @PatchMapping("/bookings/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body) {
        portalService.cancelBooking(id, body == null ? null : body.get("note"));
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", null));
    }

    @PatchMapping("/payments/{id}/refund")
    public ResponseEntity<ApiResponse<Void>> refundPayment(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, String> body) {
        portalService.refundPayment(id, body == null ? null : body.get("note"));
        return ResponseEntity.ok(ApiResponse.success("Payment refunded", null));
    }

    @PostMapping("/announcements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAnnouncement(
            @Valid @RequestBody AdminAnnouncementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Announcement published", portalService.createAnnouncement(request)));
    }

    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<AdminProfileResponse>> createAdmin(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Administrator created", portalService.createAdmin(request)));
    }

    @PatchMapping("/admins/{id}/role")
    public ResponseEntity<ApiResponse<AdminProfileResponse>> updateAdminRole(@PathVariable Long id,
                                                                              @Valid @RequestBody AdminRoleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Administrator role updated", portalService.updateAdminRole(id, request)));
    }
}
