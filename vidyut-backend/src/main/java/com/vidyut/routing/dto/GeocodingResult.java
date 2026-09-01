package com.vidyut.routing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeocodingResult(
        String lat,
        String lon,
        @JsonProperty("display_name") String displayName
) {
}
