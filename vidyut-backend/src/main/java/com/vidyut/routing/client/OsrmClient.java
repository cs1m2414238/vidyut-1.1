package com.vidyut.routing.client;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmGeometry;
import com.vidyut.routing.dto.OsrmLeg;
import com.vidyut.routing.dto.OsrmResponse;
import com.vidyut.routing.dto.OsrmRoute;
import com.vidyut.routing.dto.OsrmTableResponse;
import com.vidyut.routing.exception.OsrmException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class OsrmClient {

    private static final double ESTIMATED_ROAD_FACTOR = 1.30;
    private static final double ESTIMATED_SPEED_KPH = 50.0;

    private final RestClient restClient;
    private final RestClient referenceRestClient;
    private final GoogleRoutesClient googleRoutesClient;
    private final String profile;
    private final double snapRadiusMeters;
    private final int maxTableLocations;

    @Autowired
    public OsrmClient(
            RestClient.Builder restClientBuilder,
            @Value("${vidyut.routing.osrm.base-url}") String baseUrl,
            @Value("${vidyut.routing.osrm.reference-base-url:}") String referenceBaseUrl,
            @Value("${vidyut.routing.osrm.profile}") String profile,
            @Value("${vidyut.routing.osrm.snap-radius-meters}") double snapRadiusMeters,
            @Value("${vidyut.routing.osrm.max-table-locations}") int maxTableLocations,
            @Autowired(required = false) GoogleRoutesClient googleRoutesClient
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("OSRM base URL is required");
        }
        if (profile == null || !profile.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("OSRM profile is invalid");
        }
        this.restClient = restClientBuilder.baseUrl(stripTrailingSlash(baseUrl.trim())).build();
        this.referenceRestClient = referenceBaseUrl == null || referenceBaseUrl.isBlank()
                ? null
                : restClientBuilder.clone().baseUrl(stripTrailingSlash(referenceBaseUrl.trim())).build();
        this.googleRoutesClient = googleRoutesClient;
        this.profile = profile;
        this.snapRadiusMeters = Math.max(1, snapRadiusMeters);
        this.maxTableLocations = Math.max(3, maxTableLocations);
    }

    public OsrmClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String referenceBaseUrl,
            String profile,
            double snapRadiusMeters,
            int maxTableLocations
    ) {
        this(restClientBuilder, baseUrl, referenceBaseUrl, profile, snapRadiusMeters, maxTableLocations, null);
    }

    public OsrmResponse getRoute(Coordinate origin, Coordinate destination) {
        return getRoute(List.of(origin, destination));
    }

    public OsrmResponse getRoute(List<Coordinate> waypoints) {
        return getRoute(waypoints, RouteEngine.PRIMARY);
    }

    public OsrmResponse getRoute(List<Coordinate> waypoints, RouteEngine engine) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("At least two route waypoints are required");
        }
        waypoints.forEach(this::validateCoordinate);
        if (engine == RouteEngine.GOOGLE) {
            if (googleRoutesClient != null && googleRoutesClient.isAvailable()) {
                return googleRoutesClient.getRoute(waypoints);
            }
            throw new OsrmException("Google Routes client is not configured or available");
        }
        String coordinates = coordinateList(waypoints);
        return requestRoute(clientFor(engine), coordinates, waypoints.size());
    }

    public RouteSelection getBestRoute(List<Coordinate> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("At least two route waypoints are required");
        }
        waypoints.forEach(this::validateCoordinate);

        // 1. Try Google Routes API first when available (traffic-aware)
        if (googleRoutesClient != null && googleRoutesClient.isAvailable()) {
            try {
                OsrmResponse googleResponse = googleRoutesClient.getRoute(waypoints);
                if (googleResponse != null && googleResponse.routes() != null && !googleResponse.routes().isEmpty()) {
                    return new RouteSelection(googleResponse, RouteEngine.GOOGLE);
                }
            } catch (OsrmException ignored) {
                // Gracefully cascade to OSRM Primary -> Reference -> Estimated
            }
        }

        OsrmResponse primaryResponse = null;
        try {
            primaryResponse = getRoute(waypoints, RouteEngine.PRIMARY);
        } catch (OsrmException ignored) {
            // The optional reference engine or the conservative estimate can still plan the trip.
        }

        OsrmResponse referenceResponse = null;
        if (referenceRestClient != null) {
            try {
                referenceResponse = getRoute(waypoints, RouteEngine.REFERENCE);
            } catch (OsrmException ignored) {
                // The local engine remains usable if the optional reference service is unavailable.
            }
        }

        double primaryDistance = firstDistance(primaryResponse, waypoints.size());
        double referenceDistance = firstDistance(referenceResponse, waypoints.size());
        if (Double.isFinite(referenceDistance) && referenceDistance < primaryDistance) {
            return new RouteSelection(referenceResponse, RouteEngine.REFERENCE);
        }
        if (Double.isFinite(primaryDistance)) {
            return new RouteSelection(primaryResponse, RouteEngine.PRIMARY);
        }
        if (Double.isFinite(referenceDistance)) {
            return new RouteSelection(referenceResponse, RouteEngine.REFERENCE);
        }
        return new RouteSelection(estimatedRouteResponse(waypoints), RouteEngine.ESTIMATED);
    }

    public OsrmTableResponse getTable(Coordinate origin, List<Coordinate> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            throw new IllegalArgumentException("At least one destination is required");
        }
        validateCoordinate(origin);
        destinations.forEach(this::validateCoordinate);

        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(origin);
        coordinates.addAll(destinations);
        String destinationIndexes = IntStream.rangeClosed(1, destinations.size())
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(";"));

        return requestTable(
                coordinateList(coordinates),
                "0",
                destinationIndexes,
                coordinates.size()
        );
    }

    public OsrmTableResponse getMatrixTable(
            Coordinate origin,
            List<Coordinate> stationCoordinates,
            Coordinate destination
    ) {
        if (stationCoordinates == null || stationCoordinates.isEmpty()) {
            throw new IllegalArgumentException("At least one station is required");
        }
        validateCoordinate(origin);
        validateCoordinate(destination);
        stationCoordinates.forEach(this::validateCoordinate);

        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(origin);
        coordinates.addAll(stationCoordinates);
        coordinates.add(destination);

        int stationCount = stationCoordinates.size();
        String sources = IntStream.rangeClosed(0, stationCount)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(";"));
        String destinations = IntStream.rangeClosed(1, stationCount + 1)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(";"));

        return requestTable(coordinateList(coordinates), sources, destinations, coordinates.size());
    }

    public List<MatrixBatch> getMatrixTables(
            Coordinate origin,
            List<Coordinate> stationCoordinates,
            Coordinate destination
    ) {
        return getMatrixTables(origin, stationCoordinates, destination, RouteEngine.PRIMARY);
    }

    public List<MatrixBatch> getMatrixTables(
            Coordinate origin,
            List<Coordinate> stationCoordinates,
            Coordinate destination,
            RouteEngine engine
    ) {
        if (stationCoordinates == null || stationCoordinates.isEmpty()) {
            return List.of();
        }
        int batchSize = maxTableLocations - 2;
        List<MatrixBatch> batches = new ArrayList<>();
        for (int offset = 0; offset < stationCoordinates.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, stationCoordinates.size());
            List<Coordinate> stations = List.copyOf(stationCoordinates.subList(offset, end));
            List<Integer> stationIndexes = IntStream.range(offset, end).boxed().toList();
            addRoutableMatrixBatches(origin, stations, stationIndexes, destination, engine, batches);
        }
        return batches;
    }

    private void addRoutableMatrixBatches(
            Coordinate origin,
            List<Coordinate> stations,
            List<Integer> stationIndexes,
            Coordinate destination,
            RouteEngine engine,
            List<MatrixBatch> batches
    ) {
        try {
            batches.add(new MatrixBatch(stationIndexes, stations,
                    getMatrixTable(origin, stations, destination, engine)));
        } catch (OsrmException exception) {
            if (!exception.isLocationOutsideCoverage()) {
                throw exception;
            }
            if (stations.size() == 1) {
                return;
            }

            int middle = stations.size() / 2;
            addRoutableMatrixBatches(
                    origin,
                    List.copyOf(stations.subList(0, middle)),
                    List.copyOf(stationIndexes.subList(0, middle)),
                    destination, engine,
                    batches
            );
            addRoutableMatrixBatches(
                    origin,
                    List.copyOf(stations.subList(middle, stations.size())),
                    List.copyOf(stationIndexes.subList(middle, stationIndexes.size())),
                    destination, engine,
                    batches
            );
        }
    }

    public OsrmTableResponse getFullTable(List<Coordinate> coordinates, RouteEngine engine) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("At least two matrix locations are required");
        }
        if (coordinates.size() > maxTableLocations) {
            throw new IllegalArgumentException("The route matrix supports at most " + maxTableLocations + " locations");
        }
        coordinates.forEach(this::validateCoordinate);
        if (engine == RouteEngine.GOOGLE) {
            if (googleRoutesClient != null && googleRoutesClient.isAvailable()) {
                return googleRoutesClient.getFullTable(coordinates);
            }
            throw new OsrmException("Google Routes client is not configured or available");
        }
        try {
            return clientFor(engine).get()
                    .uri("/table/v1/{profile}/{coordinates}?annotations=distance,duration&radiuses={radiuses}",
                            profile, coordinateList(coordinates), radiuses(coordinates.size()))
                    .retrieve()
                    .body(OsrmTableResponse.class);
        } catch (RestClientException exception) {
            throw new OsrmException(
                    "OSRM could not calculate the full route matrix",
                    exception,
                    locationOutsideCoverage(exception)
            );
        }
    }

    /**
     * Recovery-only matrix: use a verified routing engine, preserving unreachable
     * cells as null. Never fill a routing outage with estimated distances.
     */
    public MatrixSelection getVerifiedFullTable(List<Coordinate> coordinates, RouteEngine preferredEngine) {
        for (RouteEngine engine : matrixEngines(preferredEngine)) {
            if (engine == RouteEngine.ESTIMATED) continue;
            try {
                OsrmTableResponse response = getFullTable(coordinates, engine);
                if (response != null && "Ok".equals(response.code())
                        && hasMatrixShape(response.distances(), coordinates.size())
                        && hasMatrixShape(response.durations(), coordinates.size())) {
                    // Null cells mean no verified road route, not permission to estimate one.
                    return new MatrixSelection(response, engine, false);
                }
            } catch (OsrmException ignored) {
                // Another real road engine may be available. Never use estimated cells.
            }
        }
        throw new OsrmException("No verified road-distance matrix is available for recovery");
    }

    public MatrixSelection getBestFullTable(List<Coordinate> coordinates, RouteEngine preferredEngine) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("At least two matrix locations are required");
        }
        if (coordinates.size() > maxTableLocations) {
            throw new IllegalArgumentException("The route matrix supports at most " + maxTableLocations + " locations");
        }
        coordinates.forEach(this::validateCoordinate);

        OsrmTableResponse estimate = estimatedTable(coordinates);
        for (RouteEngine engine : matrixEngines(preferredEngine)) {
            try {
                OsrmTableResponse response = getFullTable(coordinates, engine);
                MatrixMerge merge = mergeWithEstimate(response, estimate, coordinates.size());
                if (merge != null) {
                    return new MatrixSelection(merge.response(), engine, merge.estimatedCells());
                }
            } catch (OsrmException ignored) {
                // Try the other configured engine, then use the conservative matrix.
            }
        }
        return new MatrixSelection(estimate, RouteEngine.ESTIMATED, true);
    }

    /**
     * Builds station batches with the same row/column layout as {@link #getMatrixTables},
     * but falls back to the other configured engine and then conservative estimates.
     * This is intended for trip planning, where a temporary table outage should not
     * discard every otherwise eligible charging stop.
     */
    public List<MatrixBatch> getBestMatrixTables(
            Coordinate origin,
            List<Coordinate> stationCoordinates,
            Coordinate destination,
            RouteEngine preferredEngine
    ) {
        if (stationCoordinates == null || stationCoordinates.isEmpty()) {
            return List.of();
        }
        validateCoordinate(origin);
        validateCoordinate(destination);
        stationCoordinates.forEach(this::validateCoordinate);

        int batchSize = maxTableLocations - 2;
        List<MatrixBatch> batches = new ArrayList<>();
        for (int offset = 0; offset < stationCoordinates.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, stationCoordinates.size());
            List<Coordinate> stations = List.copyOf(stationCoordinates.subList(offset, end));
            List<Coordinate> coordinates = new ArrayList<>();
            coordinates.add(origin);
            coordinates.addAll(stations);
            coordinates.add(destination);

            MatrixSelection selection = getBestFullTable(coordinates, preferredEngine);
            batches.add(new MatrixBatch(
                    IntStream.range(offset, end).boxed().toList(),
                    stations,
                    stationMatrixView(selection.response(), stations.size())
            ));
        }
        return List.copyOf(batches);
    }

    private OsrmTableResponse requestTable(
            String coordinates,
            String sources,
            String destinations,
            int coordinateCount
    ) {
        return requestTable(restClient, coordinates, sources, destinations, coordinateCount);
    }

    private OsrmTableResponse getMatrixTable(
            Coordinate origin,
            List<Coordinate> stationCoordinates,
            Coordinate destination,
            RouteEngine engine
    ) {
        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(origin);
        coordinates.addAll(stationCoordinates);
        coordinates.add(destination);
        int stationCount = stationCoordinates.size();
        String sources = IntStream.rangeClosed(0, stationCount)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(";"));
        String destinations = IntStream.rangeClosed(1, stationCount + 1)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(";"));
        return requestTable(clientFor(engine), coordinateList(coordinates), sources, destinations, coordinates.size());
    }

    private OsrmTableResponse requestTable(
            RestClient client,
            String coordinates,
            String sources,
            String destinations,
            int coordinateCount
    ) {
        try {
            return client.get()
                    .uri("/table/v1/{profile}/{coordinates}?sources={sources}&destinations={destinations}"
                                    + "&annotations=distance,duration&radiuses={radiuses}",
                            profile, coordinates, sources, destinations, radiuses(coordinateCount))
                    .retrieve()
                    .body(OsrmTableResponse.class);
        } catch (RestClientException exception) {
            throw new OsrmException(
                    "OSRM could not calculate the route matrix",
                    exception,
                    locationOutsideCoverage(exception)
            );
        }
    }

    private OsrmResponse requestRoute(RestClient client, String coordinates, int coordinateCount) {
        try {
            return client.get()
                    .uri("/route/v1/{profile}/{coordinates}?overview=full&geometries=geojson&radiuses={radiuses}",
                            profile, coordinates, radiuses(coordinateCount))
                    .retrieve()
                    .body(OsrmResponse.class);
        } catch (RestClientException exception) {
            throw new OsrmException(
                    "OSRM could not calculate the requested route",
                    exception,
                    locationOutsideCoverage(exception)
            );
        }
    }

    private RestClient clientFor(RouteEngine engine) {
        if (engine == RouteEngine.REFERENCE) {
            if (referenceRestClient == null) {
                throw new IllegalStateException("The reference routing engine is not configured");
            }
            return referenceRestClient;
        }
        if (engine == RouteEngine.ESTIMATED) {
            throw new IllegalStateException("Estimated routing does not have an HTTP client");
        }
        return restClient;
    }

    private double firstDistance(OsrmResponse response, int waypointCount) {
        if (response == null || !"Ok".equals(response.code())
                || response.routes() == null || response.routes().isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        OsrmRoute route = response.routes().get(0);
        if (!Double.isFinite(route.distance()) || route.distance() <= 0
                || !Double.isFinite(route.duration()) || route.duration() <= 0
                || route.geometry() == null || route.geometry().coordinates() == null
                || route.geometry().coordinates().size() < 2
                || route.legs() == null || route.legs().size() != waypointCount - 1) {
            return Double.POSITIVE_INFINITY;
        }
        return route.distance();
    }

    private List<RouteEngine> matrixEngines(RouteEngine preferredEngine) {
        List<RouteEngine> engines = new ArrayList<>();
        if (googleRoutesClient != null && googleRoutesClient.isAvailable()) {
            engines.add(RouteEngine.GOOGLE);
        }
        if (preferredEngine != null && preferredEngine != RouteEngine.ESTIMATED && !engines.contains(preferredEngine)) {
            engines.add(preferredEngine);
        }
        if (!engines.contains(RouteEngine.PRIMARY)) {
            engines.add(RouteEngine.PRIMARY);
        }
        if (referenceRestClient != null && !engines.contains(RouteEngine.REFERENCE)) {
            engines.add(RouteEngine.REFERENCE);
        }
        return engines;
    }

    private MatrixMerge mergeWithEstimate(
            OsrmTableResponse response,
            OsrmTableResponse estimate,
            int locationCount
    ) {
        if (response == null || !"Ok".equals(response.code())
                || !hasMatrixShape(response.distances(), locationCount)
                || !hasMatrixShape(response.durations(), locationCount)) {
            return null;
        }
        boolean estimatedCells = false;
        List<List<Double>> distances = new ArrayList<>();
        List<List<Double>> durations = new ArrayList<>();
        for (int row = 0; row < locationCount; row++) {
            List<Double> distanceRow = new ArrayList<>();
            List<Double> durationRow = new ArrayList<>();
            for (int column = 0; column < locationCount; column++) {
                Double distance = usableMatrixValue(response.distances().get(row).get(column));
                Double duration = usableMatrixValue(response.durations().get(row).get(column));
                if (distance == null || duration == null) {
                    distance = estimate.distances().get(row).get(column);
                    duration = estimate.durations().get(row).get(column);
                    estimatedCells = true;
                }
                distanceRow.add(distance);
                durationRow.add(duration);
            }
            distances.add(List.copyOf(distanceRow));
            durations.add(List.copyOf(durationRow));
        }
        return new MatrixMerge(
                new OsrmTableResponse("Ok", List.copyOf(distances), List.copyOf(durations)),
                estimatedCells);
    }

    private boolean hasMatrixShape(List<List<Double>> matrix, int locationCount) {
        return matrix != null && matrix.size() == locationCount
                && matrix.stream().allMatch(row -> row != null && row.size() == locationCount);
    }

    private Double usableMatrixValue(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 ? value : null;
    }

    private OsrmResponse estimatedRouteResponse(List<Coordinate> waypoints) {
        List<OsrmLeg> legs = new ArrayList<>();
        double distanceMeters = 0;
        double durationSeconds = 0;
        for (int index = 1; index < waypoints.size(); index++) {
            double legDistanceMeters = estimatedRoadDistanceMeters(
                    waypoints.get(index - 1), waypoints.get(index));
            double legDurationSeconds = estimatedDurationSeconds(legDistanceMeters);
            legs.add(new OsrmLeg(legDistanceMeters, legDurationSeconds));
            distanceMeters += legDistanceMeters;
            durationSeconds += legDurationSeconds;
        }
        List<List<Double>> geometry = waypoints.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList();
        OsrmRoute route = new OsrmRoute(
                distanceMeters,
                durationSeconds,
                new OsrmGeometry("LineString", geometry),
                List.copyOf(legs));
        return new OsrmResponse("Ok", List.of(route));
    }

    private OsrmTableResponse estimatedTable(List<Coordinate> coordinates) {
        List<List<Double>> distances = new ArrayList<>();
        List<List<Double>> durations = new ArrayList<>();
        for (Coordinate from : coordinates) {
            List<Double> distanceRow = new ArrayList<>();
            List<Double> durationRow = new ArrayList<>();
            for (Coordinate to : coordinates) {
                double distanceMeters = from.equals(to) ? 0 : estimatedRoadDistanceMeters(from, to);
                distanceRow.add(distanceMeters);
                durationRow.add(estimatedDurationSeconds(distanceMeters));
            }
            distances.add(List.copyOf(distanceRow));
            durations.add(List.copyOf(durationRow));
        }
        return new OsrmTableResponse("Ok", List.copyOf(distances), List.copyOf(durations));
    }

    private OsrmTableResponse stationMatrixView(OsrmTableResponse fullTable, int stationCount) {
        List<List<Double>> distances = new ArrayList<>();
        List<List<Double>> durations = new ArrayList<>();
        for (int source = 0; source <= stationCount; source++) {
            List<Double> distanceRow = new ArrayList<>();
            List<Double> durationRow = new ArrayList<>();
            for (int destination = 1; destination <= stationCount + 1; destination++) {
                distanceRow.add(fullTable.distances().get(source).get(destination));
                durationRow.add(fullTable.durations().get(source).get(destination));
            }
            distances.add(List.copyOf(distanceRow));
            durations.add(List.copyOf(durationRow));
        }
        return new OsrmTableResponse("Ok", List.copyOf(distances), List.copyOf(durations));
    }

    private double estimatedRoadDistanceMeters(Coordinate first, Coordinate second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double straightLineKm = 12742 * Math.asin(Math.sqrt(a));
        return straightLineKm * ESTIMATED_ROAD_FACTOR * 1000.0;
    }

    private double estimatedDurationSeconds(double distanceMeters) {
        return distanceMeters / 1000.0 / ESTIMATED_SPEED_KPH * 3600.0;
    }

    private boolean locationOutsideCoverage(RestClientException exception) {
        return exception instanceof RestClientResponseException responseException
                && responseException.getResponseBodyAsString().contains("NoSegment");
    }

    private String coordinateList(List<Coordinate> coordinates) {
        return coordinates.stream()
                .map(coordinate -> coordinate.longitude() + "," + coordinate.latitude())
                .collect(Collectors.joining(";"));
    }

    private String radiuses(int coordinateCount) {
        return IntStream.range(0, coordinateCount)
                .mapToObj(ignored -> Double.toString(snapRadiusMeters))
                .collect(Collectors.joining(";"));
    }

    private void validateCoordinate(Coordinate coordinate) {
        if (coordinate == null
                || !Double.isFinite(coordinate.latitude())
                || !Double.isFinite(coordinate.longitude())
                || coordinate.latitude() < -90
                || coordinate.latitude() > 90
                || coordinate.longitude() < -180
                || coordinate.longitude() > 180) {
            throw new IllegalArgumentException("A valid latitude and longitude are required");
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }

    public record MatrixBatch(
            List<Integer> stationIndexes,
            List<Coordinate> stationCoordinates,
            OsrmTableResponse response
    ) {
    }

    public record RouteSelection(OsrmResponse response, RouteEngine engine) {
    }

    public record MatrixSelection(
            OsrmTableResponse response,
            RouteEngine engine,
            boolean estimatedCells
    ) {
    }

    private record MatrixMerge(OsrmTableResponse response, boolean estimatedCells) {
    }

    public enum RouteEngine {
        GOOGLE,
        PRIMARY,
        REFERENCE,
        ESTIMATED
    }
}
