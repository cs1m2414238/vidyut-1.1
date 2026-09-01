package com.vidyut.routing.dto;

import java.util.List;

public record OsrmRoute(
        double distance,
        double duration,
        OsrmGeometry geometry,
        List<OsrmLeg> legs
) {
}
