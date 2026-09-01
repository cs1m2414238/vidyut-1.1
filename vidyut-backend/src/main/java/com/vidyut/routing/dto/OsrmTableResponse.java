package com.vidyut.routing.dto;

import java.util.List;

public record OsrmTableResponse(

        String code,

        List<List<Double>> distances,

        List<List<Double>> durations
) {
}