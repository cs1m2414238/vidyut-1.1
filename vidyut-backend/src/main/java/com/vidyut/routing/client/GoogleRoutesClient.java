package com.vidyut.routing.client;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmGeometry;
import com.vidyut.routing.dto.OsrmLeg;
import com.vidyut.routing.dto.OsrmResponse;
import com.vidyut.routing.dto.OsrmRoute;
import com.vidyut.routing.dto.OsrmTableResponse;
import com.vidyut.routing.exception.OsrmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Primary traffic-aware routing client utilizing Google Maps Platform Routes API v2.
 * Falls back to OSRM when unconfigured or unavailable.
 */
@Component
public class GoogleRoutesClient {

    private static final Logger logger = LoggerFactory.getLogger(GoogleRoutesClient.class);
    private static final String GOOGLE_ROUTES_BASE_URL = "https://routes.googleapis.com";

    private final RestClient restClient;
    private final String apiKey;
    private final boolean enabled;

    public GoogleRoutesClient(
            RestClient.Builder restClientBuilder,
            @Value("${vidyut.routing.google.api-key:${GOOGLE_MAPS_API_KEY:${GOOGLE_ROUTES_API_KEY:}}}") String apiKey,
            @Value("${vidyut.routing.google.enabled:true}") boolean enabled
    ) {
        this.restClient = restClientBuilder
                .baseUrl(GOOGLE_ROUTES_BASE_URL)
                .build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.enabled = enabled;
    }

    public boolean isAvailable() {
        return enabled && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Computes driving route using Google Routes API v2 (Directions).
     */
    @SuppressWarnings("unchecked")
    public OsrmResponse getRoute(List<Coordinate> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("At least two waypoints are required");
        }
        if (!isAvailable()) {
            throw new OsrmException("Google Routes API is not configured or disabled");
        }

        Coordinate origin = waypoints.get(0);
        Coordinate destination = waypoints.get(waypoints.size() - 1);

        Map<String, Object> originObj = Map.of("location", Map.of("latLng", Map.of(
                "latitude", origin.latitude(),
                "longitude", origin.longitude()
        )));

        Map<String, Object> destinationObj = Map.of("location", Map.of("latLng", Map.of(
                "latitude", destination.latitude(),
                "longitude", destination.longitude()
        )));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("origin", originObj);
        requestBody.put("destination", destinationObj);

        if (waypoints.size() > 2) {
            List<Map<String, Object>> intermediates = new ArrayList<>();
            for (int i = 1; i < waypoints.size() - 1; i++) {
                Coordinate intermediate = waypoints.get(i);
                intermediates.add(Map.of("location", Map.of("latLng", Map.of(
                        "latitude", intermediate.latitude(),
                        "longitude", intermediate.longitude()
                ))));
            }
            requestBody.put("intermediates", intermediates);
        }

        requestBody.put("travelMode", "DRIVE");
        requestBody.put("routingPreference", "TRAFFIC_AWARE");
        requestBody.put("computeAlternativeRoutes", false);
        requestBody.put("languageCode", "en-IN");
        requestBody.put("units", "METRIC");

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || !response.containsKey("routes")) {
                throw new OsrmException("No routes returned by Google Routes API");
            }

            List<Map<String, Object>> routesList = (List<Map<String, Object>>) response.get("routes");
            if (routesList == null || routesList.isEmpty()) {
                throw new OsrmException("Empty route list returned by Google Routes API");
            }

            Map<String, Object> primaryRoute = routesList.get(0);
            double distanceMeters = ((Number) primaryRoute.getOrDefault("distanceMeters", 0)).doubleValue();
            double durationSeconds = parseDurationToSeconds((String) primaryRoute.getOrDefault("duration", "0s"));

            Map<String, Object> polylineMap = (Map<String, Object>) primaryRoute.get("polyline");
            String encodedPolyline = polylineMap != null ? (String) polylineMap.get("encodedPolyline") : "";
            List<List<Double>> coordinates = decodePolyline(encodedPolyline);
            if (coordinates.isEmpty()) {
                coordinates = List.of(
                        List.of(origin.longitude(), origin.latitude()),
                        List.of(destination.longitude(), destination.latitude())
                );
            }

            List<OsrmLeg> legs = new ArrayList<>();
            List<Map<String, Object>> rawLegs = (List<Map<String, Object>>) primaryRoute.get("legs");
            if (rawLegs != null) {
                for (Map<String, Object> rawLeg : rawLegs) {
                    double legDist = ((Number) rawLeg.getOrDefault("distanceMeters", 0)).doubleValue();
                    double legDur = parseDurationToSeconds((String) rawLeg.getOrDefault("duration", "0s"));
                    legs.add(new OsrmLeg(legDist, legDur));
                }
            }

            if (legs.isEmpty()) {
                legs.add(new OsrmLeg(distanceMeters, durationSeconds));
            }

            OsrmRoute route = new OsrmRoute(
                    distanceMeters,
                    durationSeconds,
                    new OsrmGeometry("LineString", coordinates),
                    List.copyOf(legs)
            );

            return new OsrmResponse("Ok", List.of(route));

        } catch (RestClientException ex) {
            logger.warn("Google Routes computeRoutes error: {}", ex.getMessage());
            throw new OsrmException("Google Routes API failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Decodes Google Encoded Polyline algorithm into GeoJSON format [[lng, lat], ...].
     */
    public static List<List<Double>> decodePolyline(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Collections.emptyList();
        }
        List<List<Double>> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                if (index >= len) break;
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latitude = lat / 1E5;
            double longitude = lng / 1E5;
            poly.add(List.of(longitude, latitude)); // GeoJSON format: [longitude, latitude]
        }

        return List.copyOf(poly);
    }

    /**
     * Computes distance and duration matrix using Google Routes Matrix API v2.
     */
    @SuppressWarnings("unchecked")
    public OsrmTableResponse getFullTable(List<Coordinate> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("At least two coordinates are required for matrix computation");
        }
        if (!isAvailable()) {
            throw new OsrmException("Google Routes API is not configured or disabled");
        }

        int count = coordinates.size();
        List<Map<String, Object>> waypoints = new ArrayList<>();
        for (Coordinate coord : coordinates) {
            waypoints.add(Map.of("waypoint", Map.of("location", Map.of("latLng", Map.of(
                    "latitude", coord.latitude(),
                    "longitude", coord.longitude()
            )))));
        }

        Map<String, Object> requestBody = Map.of(
                "origins", waypoints,
                "destinations", waypoints,
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_AWARE"
        );

        try {
            List<Map<String, Object>> response = restClient.post()
                    .uri("/distanceMatrix/v2:computeRouteMatrix")
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "originIndex,destinationIndex,duration,distanceMeters,status")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (response == null || response.isEmpty()) {
                throw new OsrmException("Empty matrix returned by Google Routes Matrix API");
            }

            // Initialize matrix NxN
            Double[][] distanceMatrix = new Double[count][count];
            Double[][] durationMatrix = new Double[count][count];

            for (Map<String, Object> element : response) {
                Integer originIndex = (Integer) element.get("originIndex");
                Integer destIndex = (Integer) element.get("destinationIndex");
                if (originIndex != null && destIndex != null && originIndex < count && destIndex < count) {
                    double dist = ((Number) element.getOrDefault("distanceMeters", 0)).doubleValue();
                    double dur = parseDurationToSeconds((String) element.getOrDefault("duration", "0s"));
                    distanceMatrix[originIndex][destIndex] = dist;
                    durationMatrix[originIndex][destIndex] = dur;
                }
            }

            List<List<Double>> distances = new ArrayList<>();
            List<List<Double>> durations = new ArrayList<>();

            for (int r = 0; r < count; r++) {
                List<Double> distRow = new ArrayList<>();
                List<Double> durRow = new ArrayList<>();
                for (int c = 0; c < count; c++) {
                    distRow.add(distanceMatrix[r][c] != null ? distanceMatrix[r][c] : 0.0);
                    durRow.add(durationMatrix[r][c] != null ? durationMatrix[r][c] : 0.0);
                }
                distances.add(List.copyOf(distRow));
                durations.add(List.copyOf(durRow));
            }

            return new OsrmTableResponse("Ok", List.copyOf(distances), List.copyOf(durations));

        } catch (RestClientException ex) {
            logger.warn("Google computeRouteMatrix error: {}", ex.getMessage());
            throw new OsrmException("Google Route Matrix API failed: " + ex.getMessage(), ex);
        }
    }

    private double parseDurationToSeconds(String durationString) {
        if (durationString == null || durationString.isBlank()) {
            return 0.0;
        }
        try {
            String clean = durationString.trim().replace("s", "");
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
