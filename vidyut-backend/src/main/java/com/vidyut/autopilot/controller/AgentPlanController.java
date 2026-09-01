package com.vidyut.autopilot.controller;

import com.vidyut.autopilot.dto.*;
import com.vidyut.autopilot.service.AutopilotService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.routing.dto.RouteStationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentPlanController {
    private final AutopilotService autopilotService;
    private final CurrentUserUtil currentUser;

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<AutopilotPlanResponse>> plan(
            @Valid @RequestBody AutopilotTripRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Timing-matched plan ready",
                autopilotService.previewTrip(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping("/plans/{tripId}")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> plan(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.getTrip(tripId, currentUser.getCurrentAccountId())));
    }

    @GetMapping("/plans/{tripId}/legs/{stopId}/alternatives")
    public ResponseEntity<ApiResponse<List<RouteStationResponse>>> alternatives(
            @PathVariable Long tripId, @PathVariable Long stopId) {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.stopAlternatives(tripId, stopId, currentUser.getCurrentAccountId())));
    }

    @PostMapping("/plans/{tripId}/legs/{stopId}/swap")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> swap(
            @PathVariable Long tripId, @PathVariable Long stopId,
            @Valid @RequestBody AutopilotStopSwapRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stop swapped and downstream ETAs recalculated",
                autopilotService.swapStop(tripId, stopId, request.getStationId(),
                        currentUser.getCurrentAccountId())));
    }

    @PostMapping("/plans/{tripId}/simulate-delay")
    public ResponseEntity<ApiResponse<AutopilotTripResponse>> delay(
            @PathVariable Long tripId, @Valid @RequestBody AutopilotDelayRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Delay handled and trip replanned",
                autopilotService.simulateDelay(tripId, currentUser.getCurrentAccountId(),
                        request.getDelayMinutes())));
    }

    @GetMapping("/trips/{tripId}/summary")
    public ResponseEntity<ApiResponse<AutopilotTripSummaryResponse>> summary(@PathVariable Long tripId) {
        return ResponseEntity.ok(ApiResponse.success(
                autopilotService.summary(tripId, currentUser.getCurrentAccountId())));
    }
}
