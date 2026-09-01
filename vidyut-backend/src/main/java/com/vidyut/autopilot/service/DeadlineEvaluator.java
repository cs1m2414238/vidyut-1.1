package com.vidyut.autopilot.service;

import com.vidyut.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class DeadlineEvaluator {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public DeadlineAssessment assess(String arriveBy, LocalTime departure, int totalMinutes) {
        LocalTime normalizedDeparture = departure.withSecond(0).withNano(0);
        LocalTime estimatedArrival = normalizedDeparture.plusMinutes(totalMinutes);
        if (arriveBy == null || arriveBy.isBlank()) {
            return new DeadlineAssessment(
                    false, null, estimatedArrival, null, true, 0);
        }

        LocalTime requestedArrival;
        try {
            requestedArrival = LocalTime.parse(arriveBy.trim(), CLOCK);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("Arrival deadline must use HH:mm format");
        }

        long availableMinutes = Duration.between(normalizedDeparture, requestedArrival).toMinutes();
        if (availableMinutes < 0) availableMinutes += 24 * 60;
        boolean feasible = totalMinutes <= availableMinutes;
        return new DeadlineAssessment(
                true,
                requestedArrival,
                estimatedArrival,
                (int) availableMinutes,
                feasible,
                feasible ? 0 : totalMinutes - (int) availableMinutes);
    }

    public record DeadlineAssessment(
            boolean specified,
            LocalTime requestedArrivalTime,
            LocalTime estimatedArrivalTime,
            Integer availableMinutes,
            boolean feasible,
            int minutesLate
    ) {
    }
}
