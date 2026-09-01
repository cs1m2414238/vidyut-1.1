package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.RouteExperienceOutcome;

import java.time.LocalDateTime;

public record RouteExperienceResponse(
        Long id,
        Long tripId,
        Long stationId,
        String origin,
        String destination,
        RouteExperienceOutcome outcome,
        String detail,
        Integer rating,
        Integer delayMinutes,
        LocalDateTime createdAt
) {}
