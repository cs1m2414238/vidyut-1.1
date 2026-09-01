package com.vidyut.routing.service;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCorridorServiceTest {

    private final RouteCorridorService corridor = new RouteCorridorService();

    @Test
    void measuresOffsetAndProgressAgainstTheActualRoutePolyline() {
        OsrmGeometry geometry = new OsrmGeometry("LineString", List.of(
                List.of(77.0, 28.0),
                List.of(77.0, 27.0),
                List.of(78.0, 27.0)
        ));

        var match = corridor.match(new Coordinate(27.0, 77.05), geometry);

        assertThat(match.offsetKm()).isLessThan(0.2);
        assertThat(match.progressKm()).isBetween(110.0, 117.0);
    }

    @Test
    void rejectsAChargerFarFromThePolylineEvenWhenInsideItsBoundingBox() {
        OsrmGeometry geometry = new OsrmGeometry("LineString", List.of(
                List.of(77.0, 28.0),
                List.of(78.0, 27.0),
                List.of(79.0, 26.0)
        ));

        var match = corridor.match(new Coordinate(27.8, 78.8), geometry);

        assertThat(match.offsetKm()).isGreaterThan(90);
    }
}
