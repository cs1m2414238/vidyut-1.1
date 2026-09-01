package com.vidyut.routing.dto;

public record StationRouteMetric(
        Long stationId,
        double distanceFromOriginKm,
        int durationFromOriginMinutes,
        double distanceToDestinationKm,
        int durationToDestinationMinutes,
        double detourKm
) {
    public double distanceKm() {
        return distanceFromOriginKm;
    }

    public int durationMinutes() {
        return durationFromOriginMinutes;
    }
}