package com.vidyut.routing.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.dto.RoutePlanResponse;
import com.vidyut.routing.dto.RouteStationResponse;
import com.vidyut.routing.dto.RouteStatusResponse;
import com.vidyut.routing.dto.DiversionRequest;
import com.vidyut.routing.dto.DiversionResponse;
import com.vidyut.routing.service.RoutingService;
import com.vidyut.common.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;
    private final CurrentUserUtil currentUser;

    @PostMapping("/plan")
    public ResponseEntity<ApiResponse<RoutePlanResponse>> planRoute(@Valid @RequestBody RoutePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Route planned successfully",
                routingService.planRoute(request, currentUser.getCurrentAccountId())));
    }

    @GetMapping("/alternatives")
    public ResponseEntity<ApiResponse<java.util.List<RouteStationResponse>>> alternatives(
            @RequestParam Long stationId, @RequestParam(required = false) Long vehicleId) {
        return ResponseEntity.ok(ApiResponse.success(routingService.alternatives(
                currentUser.getCurrentAccountId(), stationId, vehicleId)));
    }

    @GetMapping("/status/{bookingId}")
    public ResponseEntity<ApiResponse<RouteStatusResponse>> status(@PathVariable Long bookingId) {
        return ResponseEntity.ok(ApiResponse.success(routingService.routeStatus(
                currentUser.getCurrentAccountId(), bookingId)));
    }

    @PostMapping("/divert/{bookingId}")
    public ResponseEntity<ApiResponse<DiversionResponse>> divert(
            @PathVariable Long bookingId, @Valid @RequestBody DiversionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Route diversion completed", routingService.divert(
                currentUser.getCurrentAccountId(), bookingId, request.getAlternativeStationId())));
    }

}
