package com.vidyut.routing.service;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmGeometry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteCorridorService {

    private static final double KM_PER_LATITUDE_DEGREE = 110.574;
    private static final double KM_PER_LONGITUDE_DEGREE = 111.320;

    public RouteMatch match(Coordinate point, OsrmGeometry geometry) {
        if (point == null || geometry == null || geometry.coordinates() == null
                || geometry.coordinates().size() < 2) {
            throw new IllegalArgumentException("A route polyline with at least two points is required");
        }

        double bestOffsetKm = Double.POSITIVE_INFINITY;
        double bestProgressKm = 0;
        double cumulativeKm = 0;
        List<List<Double>> coordinates = geometry.coordinates();

        for (int index = 1; index < coordinates.size(); index++) {
            Coordinate start = coordinate(coordinates.get(index - 1));
            Coordinate end = coordinate(coordinates.get(index));
            double segmentKm = haversineKm(start, end);
            double meanLatitudeRadians = Math.toRadians(
                    (start.latitude() + end.latitude() + point.latitude()) / 3.0);
            double longitudeScale = KM_PER_LONGITUDE_DEGREE * Math.cos(meanLatitudeRadians);

            double segmentX = (end.longitude() - start.longitude()) * longitudeScale;
            double segmentY = (end.latitude() - start.latitude()) * KM_PER_LATITUDE_DEGREE;
            double pointX = (point.longitude() - start.longitude()) * longitudeScale;
            double pointY = (point.latitude() - start.latitude()) * KM_PER_LATITUDE_DEGREE;
            double squaredLength = segmentX * segmentX + segmentY * segmentY;
            double projection = squaredLength == 0
                    ? 0
                    : clamp((pointX * segmentX + pointY * segmentY) / squaredLength, 0, 1);
            double offsetKm = Math.hypot(
                    pointX - projection * segmentX,
                    pointY - projection * segmentY);

            if (offsetKm < bestOffsetKm) {
                bestOffsetKm = offsetKm;
                bestProgressKm = cumulativeKm + projection * segmentKm;
            }
            cumulativeKm += segmentKm;
        }

        return new RouteMatch(round(bestOffsetKm), round(bestProgressKm));
    }

    private Coordinate coordinate(List<Double> geoJsonCoordinate) {
        if (geoJsonCoordinate == null || geoJsonCoordinate.size() < 2) {
            throw new IllegalArgumentException("The route polyline contains an invalid coordinate");
        }
        return new Coordinate(geoJsonCoordinate.get(1), geoJsonCoordinate.get(0));
    }

    private double haversineKm(Coordinate first, Coordinate second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 12742 * Math.asin(Math.sqrt(a));
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record RouteMatch(double offsetKm, double progressKm) {
    }
}
