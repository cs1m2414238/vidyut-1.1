package com.vidyut.routing.client;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.GeocodingResult;
import com.vidyut.routing.exception.OsrmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Optional;

@Component
public class GeocodingClient {

    private final RestClient restClient;
    private final String countryCodes;
    private final long minimumRequestIntervalMs;
    private final Object requestLock = new Object();
    private long lastRequestStartedAt;

    public GeocodingClient(
            RestClient.Builder restClientBuilder,
            @Value("${vidyut.routing.geocoder.base-url}") String baseUrl,
            @Value("${vidyut.routing.geocoder.user-agent}") String userAgent,
            @Value("${vidyut.routing.geocoder.country-codes}") String countryCodes,
            @Value("${vidyut.routing.geocoder.minimum-request-interval-ms}") long minimumRequestIntervalMs
    ) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Geocoder base URL is required");
        }
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("Geocoder user agent is required");
        }
        this.restClient = restClientBuilder
                .baseUrl(baseUrl.trim().replaceFirst("/+$", ""))
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent.trim())
                .build();
        this.countryCodes = countryCodes == null ? "" : countryCodes.trim();
        this.minimumRequestIntervalMs = Math.max(0, minimumRequestIntervalMs);
    }

    public Optional<Coordinate> geocode(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        synchronized (requestLock) {
            waitForRateLimit();
            lastRequestStartedAt = System.currentTimeMillis();
            try {
                GeocodingResult[] results = restClient.get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/search")
                                    .queryParam("q", query.trim())
                                    .queryParam("format", "jsonv2")
                                    .queryParam("limit", 1);
                            if (!countryCodes.isBlank()) {
                                builder.queryParam("countrycodes", countryCodes);
                            }
                            return builder.build();
                        })
                        .retrieve()
                        .body(GeocodingResult[].class);
                return Arrays.stream(results == null ? new GeocodingResult[0] : results)
                        .map(this::toCoordinate)
                        .flatMap(Optional::stream)
                        .findFirst();
            } catch (RestClientException exception) {
                throw new OsrmException("The configured location lookup service is unavailable", exception);
            }
        }
    }

    private Optional<Coordinate> toCoordinate(GeocodingResult result) {
        try {
            double latitude = Double.parseDouble(result.lat());
            double longitude = Double.parseDouble(result.lon());
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90
                    || longitude < -180 || longitude > 180) {
                return Optional.empty();
            }
            return Optional.of(new Coordinate(latitude, longitude));
        } catch (NullPointerException | NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private void waitForRateLimit() {
        long waitMs = minimumRequestIntervalMs - (System.currentTimeMillis() - lastRequestStartedAt);
        if (waitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OsrmException("Location lookup was interrupted", exception);
        }
    }
}
