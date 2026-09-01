package com.vidyut.routing.service;

import com.vidyut.routing.dto.RoutePlanRequest;
import com.vidyut.routing.dto.RoutePlanResponse;
import com.vidyut.routing.dto.RouteStationResponse;
import com.vidyut.routing.dto.RouteStatusResponse;
import com.vidyut.routing.dto.DiversionResponse;

import java.util.List;

public interface RoutingService {
    RoutePlanResponse planRoute(RoutePlanRequest request, Long userId);
    List<RouteStationResponse> alternatives(Long userId, Long stationId, Long vehicleId);
    RouteStatusResponse routeStatus(Long userId, Long bookingId);
    DiversionResponse divert(Long userId, Long bookingId, Long alternativeStationId);

}
