package com.vidyut.routing.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteStatusResponse {
    private Long bookingId;
    private Long stationId;
    private String stationStatus;
    private boolean diversionRecommended;
    private String reason;
    private List<RouteStationResponse> alternatives;
}
