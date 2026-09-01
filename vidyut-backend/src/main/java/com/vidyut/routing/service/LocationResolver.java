package com.vidyut.routing.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.client.GeocodingClient;
import com.vidyut.routing.dto.Coordinate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LocationResolver {

    private static final Pattern COORDINATES = Pattern.compile(
            "^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,;]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$"
    );

    private final GeocodingClient geocodingClient;
    private final Map<String, Coordinate> cache = new LinkedHashMap<>(128, 0.75f, true);

    @Value("${vidyut.routing.geocoder.cache-size}")
    private int cacheSize;

    public Coordinate resolve(String label) {
        if (label == null || label.isBlank()) {
            throw new BadRequestException("Location is required");
        }

        Matcher coordinateMatcher = COORDINATES.matcher(label);
        if (coordinateMatcher.matches()) {
            return validated(
                    Double.parseDouble(coordinateMatcher.group(1)),
                    Double.parseDouble(coordinateMatcher.group(2))
            );
        }

        String cacheKey = label.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        synchronized (cache) {
            Coordinate cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        Coordinate resolved = geocodingClient.geocode(label)
                .orElseThrow(() -> new BadRequestException(
                        "Could not find '" + label.trim() + "'. Enter a more specific place or address."
                ));
        synchronized (cache) {
            cache.put(cacheKey, resolved);
            while (cache.size() > Math.max(1, cacheSize)) {
                cache.remove(cache.keySet().iterator().next());
            }
        }
        return resolved;
    }

    public Coordinate resolve(Double latitude, Double longitude, String label) {
        if (latitude == null && longitude == null) {
            return resolve(label);
        }
        if (latitude == null || longitude == null) {
            throw new BadRequestException("Both latitude and longitude are required");
        }
        return validated(latitude, longitude);
    }

    private Coordinate validated(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new BadRequestException("Location coordinates are outside the valid latitude/longitude range");
        }
        return new Coordinate(latitude, longitude);
    }
}
