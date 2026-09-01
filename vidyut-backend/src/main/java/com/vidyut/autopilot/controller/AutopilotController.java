package com.vidyut.autopilot.controller;

import java.util.Map;
import com.vidyut.autopilot.dto.AutopilotProgressRequest;
import com.vidyut.autopilot.dto.AutopilotPlanResponse;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.dto.JourneyIntentParseRequest;
import com.vidyut.autopilot.dto.JourneyIntentParseResponse;
import com.vidyut.autopilot.dto.RouteExperienceRequest;
import com.vidyut.autopilot.dto.RouteExperienceResponse;
import com.vidyut.autopilot.dto.VehicleRecommendationRequest;
import com.vidyut.autopilot.dto.VehicleRecommendationResponse;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.autopilot.service.AutopilotFaultWorkflowService;
import com.vidyut.autopilot.service.JourneyIntentParser;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/ev/autopilot", "/api/autopilot"})
@RequiredArgsConstructor
public class AutopilotController {

    private final AutopilotService autopilotService;
    private final AutopilotFaultWorkflowService faultWorkflowService;
    private final JourneyIntentParser journeyIntentParser;
    private final CurrentUserUtil currentUser;
    private final com.vidyut.agent.service.AiAgentGateway agentGateway;

    public record RecoveryRequest(@jakarta.validation.constraints.NotBlank String incidentId,
                                  String planId, String provider) {}

    @PostMapping("/trips/{tripId}/position")
    public ResponseEntity<?> updatePosition(@PathVariable Long tripId,
            @Valid @RequestBody com.vidyut.autopilot.dto.AutopilotPositionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.updatePosition(tripId, currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/trips/{tripId}/recovery/context")
    public ResponseEntity<?> recoveryContext(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.recoveryContext(tripId, currentUser.getCurrentAccountId(), request.incidentId())));
    }

    @PostMapping("/trips/{tripId}/recovery/candidates")
    public ResponseEntity<?> recoveryCandidates(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.safeRecoveryCandidates(tripId, currentUser.getCurrentAccountId(), request.incidentId())));
    }

    @PostMapping("/trips/{tripId}/recovery/prepare")
    public ResponseEntity<?> prepareRecovery(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.prepareSafeReroute(tripId, currentUser.getCurrentAccountId(), request.incidentId(), request.planId(), request.provider())));
    }

    @PostMapping("/trips/{tripId}/recovery/execute")
    public ResponseEntity<?> executeRecovery(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.executeAgentReroute(tripId, currentUser.getCurrentAccountId(), request.incidentId(), request.planId())));
    }

    @PostMapping("/trips/{tripId}/recovery/refresh")
    public ResponseEntity<?> refreshRecovery(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(autopilotService.refreshRecovery(tripId, currentUser.getCurrentAccountId(), request.incidentId())));
    }

    @PostMapping("/trips/{tripId}/recovery/run")
    public ResponseEntity<?> runRecovery(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request,
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization) {
        // Ownership check finishes before Python calls back; do not hold a trip
        // transaction/lock across this agent request.
        autopilotService.recoveryContext(tripId, currentUser.getCurrentAccountId(), request.incidentId());
        return ResponseEntity.ok(ApiResponse.success(agentGateway.recover(authorization, tripId, request.incidentId())));
    }

    @PostMapping("/intent/parse")
    public ResponseEntity<ApiResponse<JourneyIntentParseResponse>> parseJourneyIntent(
            @Valid @RequestBody JourneyIntentParseRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Journey intent converted into enforceable fields",
                journeyIntentParser.parse(request.getText())));
    }

    @PostMapping("/vehicles/recommend")
    public ResponseEntity<ApiResponse<VehicleRecommendationResponse>> recommendVehicle(
            @Valid @RequestBody VehicleRecommendationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Owned vehicles compared against the live route",
                autopilotService.recommendVehicle(currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/trips/preview")
    public ResponseEntity<ApiResponse<AutopilotPlanResponse>> previewTrip(
            @Valid @RequestBody AutopilotTripRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Autopilot proposal created without booking",
                autopilotService.previewTrip(currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/trips")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> launchTrip(
            @Valid @RequestBody AutopilotTripRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Autopilot planned and reserved the trip",
                autopilotService.launchTrip(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping("/trips/current")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> getCurrentTrip() {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.getCurrentTrip(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/trips/{tripId}")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> getTrip(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.getTrip(tripId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/trips/{tripId}/start")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> startJourney(
            @PathVariable Long tripId,
            @Valid @RequestBody(required = false) AutopilotProgressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Journey monitoring started",
                autopilotService.startJourney(tripId, currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping({"/trips/{tripId}/simulate-fault", "/trips/{tripId}/report-issue", "/trips/{tripId}/charger-issues", "/journeys/{tripId}/charger-issues", "/journeys/{tripId}/report-issue"})
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> simulateFault(
            @PathVariable Long tripId,
            @RequestBody(required = false) Map<String, String> request
    ) {
        String reason = "CHARGER_NOT_STARTING";
        if (request != null) {
            if (request.get("issueCategory") != null && !request.get("issueCategory").isBlank()) {
                reason = request.get("issueCategory");
            } else if (request.get("reason") != null && !request.get("reason").isBlank()) {
                reason = request.get("reason");
            }
        }
        String comment = request != null ? request.get("comment") : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Issue recorded for the EV Agent; reservations remain unchanged until permitted execution",
                faultWorkflowService.simulateAndPropagate(tripId, currentUser.getCurrentAccountId(), reason, comment)));
    }

    @PostMapping("/trips/{tripId}/complete-charging")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> completeCharging(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success("Charging completion processed",
                autopilotService.completeCharging(tripId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/trips/{tripId}/approve-reroute")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> approveReroute(@PathVariable Long tripId, @Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Replacement charger approved and reserved",
                autopilotService.approvePreparedReroute(tripId, currentUser.getCurrentAccountId(), request.incidentId(), request.planId())));
    }

    @PostMapping("/trips/{tripId}/experience")
    public ResponseEntity<ApiResponse<RouteExperienceResponse>> recordExperience(
            @PathVariable Long tripId,
            @Valid @RequestBody RouteExperienceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Route experience stored for future planning",
                autopilotService.recordExperience(tripId, currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping({"/trips/{tripId}/end", "/trips/{tripId}/cancel", "/journeys/{tripId}/end"})
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> endJourney(
            @PathVariable Long tripId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Journey ended and active reservations released",
                autopilotService.endJourney(tripId, currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/trips/{tripId}/simulation/arrive")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> simulateArrival(
            @PathVariable Long tripId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle arrived at destination",
                autopilotService.simulateArrival(tripId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/stations/reset-demo")
    public ResponseEntity<ApiResponse<String>> resetDemoStations() {
        throw new com.vidyut.common.exception.ForbiddenException("Restore demo charger hardware from the Company workspace after approval");
    }
}
