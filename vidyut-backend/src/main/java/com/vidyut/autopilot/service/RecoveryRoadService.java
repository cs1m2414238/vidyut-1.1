package com.vidyut.autopilot.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** Recovery must fail closed when a road engine cannot verify a leg. */
@Service @RequiredArgsConstructor
public class RecoveryRoadService {
    private final OsrmClient client;

    public RoadRoute route(List<Coordinate> points) {
        OsrmClient.RouteSelection result = client.getBestRoute(points);
        if (result == null || result.engine() == OsrmClient.RouteEngine.ESTIMATED
                || result.response() == null || !"Ok".equals(result.response().code())
                || result.response().routes() == null || result.response().routes().isEmpty()) {
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: recovery requires actual road distances");
        }
        OsrmRoute route = result.response().routes().get(0);
        if (route.legs() == null || route.legs().size() != points.size() - 1
                || !finiteNonnegative(route.distance()) || !finiteNonnegative(route.duration())
                || route.legs().stream().anyMatch(l -> !finiteNonnegative(l.distance()) || !finiteNonnegative(l.duration()))
                || route.geometry() == null || route.geometry().coordinates() == null
                || route.geometry().coordinates().size() < 2) {
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: missing road geometry or journey legs");
        }
        List<List<Double>> geometry = route.geometry().coordinates();
        if (geometry.stream().anyMatch(xy -> xy==null || xy.size()<2 || xy.get(0)==null || xy.get(1)==null
                || !Double.isFinite(xy.get(0)) || !Double.isFinite(xy.get(1)) || Math.abs(xy.get(0))>180 || Math.abs(xy.get(1))>90))
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: invalid road coordinates");
        if (distanceKm(points.get(0), point(geometry.get(0))) > 0.5
                || distanceKm(points.get(points.size() - 1), point(geometry.get(geometry.size() - 1))) > 0.5) {
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: route snaps too far from the vehicle or charger");
        }
        double legsDistance = route.legs().stream().mapToDouble(OsrmLeg::distance).sum();
        var corridor = new com.vidyut.routing.service.RouteCorridorService();
        if (points.stream().anyMatch(p -> corridor.match(p,route.geometry()).offsetKm()>0.5))
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: a charger waypoint is not on the returned road route");
        if (Math.abs(legsDistance - route.distance()) > Math.max(10, route.distance() * 0.001)) {
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: route totals disagree with its legs");
        }
        return new RoadRoute(route, result.engine());
    }

    public OsrmTableResponse matrix(List<Coordinate> points, OsrmClient.RouteEngine engine) {
        OsrmClient.MatrixSelection result = client.getVerifiedFullTable(points, engine);
        if (result == null || result.engine() == OsrmClient.RouteEngine.ESTIMATED || result.estimatedCells()) {
            throw new BadRequestException("ROAD_ROUTE_UNVERIFIED: estimated matrix cells are not safe recovery evidence");
        }
        return result.response();
    }

    static boolean finiteNonnegative(double value) { return Double.isFinite(value) && value >= 0; }
    static Coordinate point(List<Double> xy) { return new Coordinate(xy.get(1), xy.get(0)); }
    public static double distanceKm(Coordinate a, Coordinate b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(dLon / 2), 2);
        return 12742 * Math.asin(Math.sqrt(Math.min(1, h)));
    }
    public record RoadRoute(OsrmRoute route, OsrmClient.RouteEngine engine) {}
}
