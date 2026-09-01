package com.vidyut.autopilot.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineEvaluatorTest {

    private final DeadlineEvaluator evaluator = new DeadlineEvaluator();

    @Test
    void reportsTheExactDeadlineMissInsteadOfCallingThePlanFeasible() {
        var result = evaluator.assess("05:44", LocalTime.of(2, 0), 478);

        assertThat(result.estimatedArrivalTime()).isEqualTo(LocalTime.of(9, 58));
        assertThat(result.requestedArrivalTime()).isEqualTo(LocalTime.of(5, 44));
        assertThat(result.feasible()).isFalse();
        assertThat(result.minutesLate()).isEqualTo(254);
    }

    @Test
    void treatsAnEarlierClockTimeAsTheNextDay() {
        var result = evaluator.assess("01:00", LocalTime.of(23, 30), 60);

        assertThat(result.availableMinutes()).isEqualTo(90);
        assertThat(result.feasible()).isTrue();
    }
}
