package com.vidyut.autopilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.dto.AutopilotPositionRequest;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.dto.*;
import com.vidyut.routing.service.RouteCorridorService;
import com.vidyut.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service @RequiredArgsConstructor
public class AutopilotPositionService {
    private final ObjectMapper json;
    private final RouteCorridorService corridor;

    public static boolean demoVehicle(Vehicle vehicle) {
        return vehicle.getRegistrationNumber() != null && vehicle.getRegistrationNumber().matches("DEMO-EV-\\d+");
    }

    public void setNavigation(AutopilotTrip trip, OsrmRoute route) {
        try { trip.setNavigationRouteJson(json.writeValueAsString(route)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot persist navigation route", e); }
        trip.setRouteStartDistanceKm(trip.getDistanceTravelledKm());
    }

    public OsrmRoute navigation(AutopilotTrip trip) {
        if (trip.getNavigationRouteJson() == null) throw new BadRequestException("POSITION_REQUIRED: refresh journey telemetry before recovery");
        try { return json.readValue(trip.getNavigationRouteJson(), OsrmRoute.class); }
        catch (JsonProcessingException e) { throw new BadRequestException("POSITION_REQUIRED: stored navigation is unavailable"); }
    }

    public Coordinate current(AutopilotTrip trip) {
        if (trip.getCurrentLatitude() == null || trip.getCurrentLongitude() == null || trip.getPositionRecordedAt() == null
                || !Double.isFinite(trip.getCurrentLatitude()) || !Double.isFinite(trip.getCurrentLongitude())
                || Math.abs(trip.getCurrentLatitude()) > 90 || Math.abs(trip.getCurrentLongitude()) > 180) {
            throw new BadRequestException("POSITION_REQUIRED: no verified current vehicle position is available");
        }
        long age = Duration.between(trip.getPositionRecordedAt(), now()).getSeconds();
        if (age < -30 || (!"DEMO_ROUTE_PROGRESS".equals(trip.getPositionSource()) && age > 120)) {
            throw new BadRequestException("POSITION_REQUIRED: current GPS/SoC snapshot is stale");
        }
        return new Coordinate(trip.getCurrentLatitude(), trip.getCurrentLongitude());
    }

    public void recordGps(AutopilotTrip trip, AutopilotPositionRequest request) {
        long age = Duration.between(request.getRecordedAt(), now()).getSeconds();
        if (age < -30 || age > 120 || (trip.getPositionRecordedAt() != null
                && request.getRecordedAt().isBefore(trip.getPositionRecordedAt()))
                || !Double.isFinite(request.getBatteryPercent()) || request.getBatteryPercent() < 0
                || request.getBatteryPercent() > 100 || !Double.isFinite(request.getLatitude())
                || !Double.isFinite(request.getLongitude()) || Math.abs(request.getLatitude()) > 90
                || Math.abs(request.getLongitude()) > 180) {
            throw new BadRequestException("A fresh, valid GPS and battery snapshot is required");
        }
        trip.setCurrentLatitude(request.getLatitude());
        trip.setCurrentLongitude(request.getLongitude());
        trip.setCurrentBatteryPercent(request.getBatteryPercent());
        trip.setPositionRecordedAt(request.getRecordedAt());
        trip.setPositionSource("GPS");
        if (trip.getNavigationRouteJson() != null) {
            OsrmRoute route = navigation(trip);
            var match = corridor.match(new Coordinate(request.getLatitude(), request.getLongitude()), route.geometry());
            // Route progress is presentation/accounting only; recovery uses the GPS
            // coordinate itself and requests a new road route from that coordinate.
            if (match.offsetKm() <= 1) {
                double progress = Math.min(route.distance() / 1000, match.progressKm());
                trip.setDistanceTravelledKm(Math.max(trip.getDistanceTravelledKm(), trip.getRouteStartDistanceKm() + progress));
            }
        }
    }

    public void initializeDemo(AutopilotTrip trip, Coordinate origin) {
        trip.setCurrentLatitude(origin.latitude());
        trip.setCurrentLongitude(origin.longitude());
        trip.setPositionSource("DEMO_ROUTE_PROGRESS");
        trip.setPositionRecordedAt(now());
    }

    /** Explicit demo movement follows the persisted road polyline and consumes the
     * corresponding energy. It does not guess a point near the failed charger. */
    public void advanceDemo(AutopilotTrip trip, double drop, double capacityKwh, double energyPerKmKwh) {
        OsrmRoute route = navigation(trip);
        double oldProgress = Math.max(0, trip.getDistanceTravelledKm() - trip.getRouteStartDistanceKm());
        double actualDrop = Math.min(Math.max(0, drop), trip.getCurrentBatteryPercent());
        double requestedKm = actualDrop / 100 * capacityKwh / energyPerKmKwh;
        double travelledKm = Math.min(requestedKm, Math.max(0, route.distance() / 1000 - oldProgress));
        double progress = oldProgress + travelledKm;
        Coordinate point = along(route, progress);
        trip.setCurrentLatitude(point.latitude());
        trip.setCurrentLongitude(point.longitude());
        trip.setCurrentBatteryPercent(Math.max(0, trip.getCurrentBatteryPercent() - travelledKm * energyPerKmKwh / capacityKwh * 100));
        trip.setDistanceTravelledKm(trip.getDistanceTravelledKm() + travelledKm);
        trip.setElapsedDriveMinutes(trip.getElapsedDriveMinutes()
                + (int) Math.ceil(route.duration() / 60 * travelledKm / Math.max(0.001, route.distance() / 1000)));
        trip.setPositionSource("DEMO_ROUTE_PROGRESS");
        trip.setPositionRecordedAt(now());
    }

    static Coordinate along(OsrmRoute route, double roadKm) {
        List<List<Double>> line = route.geometry().coordinates();
        double length = 0;
        for (int i = 1; i < line.size(); i++) length += RecoveryRoadService.distanceKm(RecoveryRoadService.point(line.get(i-1)), RecoveryRoadService.point(line.get(i)));
        double remaining = length * Math.min(1, roadKm / Math.max(0.001, route.distance() / 1000));
        for (int i = 1; i < line.size(); i++) {
            Coordinate a = RecoveryRoadService.point(line.get(i-1)), b = RecoveryRoadService.point(line.get(i));
            double segment = RecoveryRoadService.distanceKm(a, b);
            if (remaining <= segment && segment > 0) {
                double ratio = remaining / segment;
                return new Coordinate(a.latitude() + ratio * (b.latitude()-a.latitude()), a.longitude() + ratio * (b.longitude()-a.longitude()));
            }
            remaining -= segment;
        }
        return RecoveryRoadService.point(line.get(line.size()-1));
    }

    static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
