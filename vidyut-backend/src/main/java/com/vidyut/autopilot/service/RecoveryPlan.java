package com.vidyut.autopilot.service;

import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.OsrmRoute;
import java.util.List;

public record RecoveryPlan(String id, String strategy, List<AutopilotStop> stops,
        OsrmRoute route, OsrmClient.RouteEngine engine, double destinationArrivalSoc,
        int driveMinutes, int chargingMinutes, int queueMinutes, int connectionMinutes,
        double cost, Double originalRemainingDistanceKm, Integer originalRemainingMinutes,
        double originalRemainingCost, double firstLegEnergyKwh) {
    public double distanceKm() { return route.distance()/1000; }
    public int totalMinutes() { return driveMinutes + chargingMinutes + queueMinutes + connectionMinutes; }
}
