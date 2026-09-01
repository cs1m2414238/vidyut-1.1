package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.JourneyIntentParseResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyIntentParserTest {

    private final JourneyIntentParser parser = new JourneyIntentParser();

    @Test
    void parsesCompleteNaturalLanguageJourney() {
        JourneyIntentParseResponse result = parser.parse(
                "Take me from Kanpur to Bhopal with current battery 76%, keep 10% reserve, "
                        + "budget ₹900, arrive by 9:30 PM, fastest, ask me before booking, with a rest stop");

        assertThat(result.getOrigin()).isEqualTo("Kanpur");
        assertThat(result.getDestination()).isEqualTo("Bhopal");
        assertThat(result.getCurrentBatteryPercent()).isEqualTo(76);
        assertThat(result.getMinimumArrivalBatteryPercent()).isEqualTo(10);
        assertThat(result.getMaximumChargingBudget()).isEqualTo(900);
        assertThat(result.getArrivalDeadline()).isEqualTo("21:30");
        assertThat(result.getOptimizeFor()).isEqualTo("TIME");
        assertThat(result.getAutonomyMode()).isEqualTo("ASK_BEFORE_ACTIONS");
        assertThat(result.getTripPurpose()).isEqualTo("REST_STOP");
    }

    @Test
    void parsesRouteWithoutFromAndIndependentControlAxes() {
        JourneyIntentParseResponse result = parser.parse(
                "Delhi to Prayagraj, battery at 50%, safety reserve 15%, under INR 1500, "
                        + "lowest cost and full autopilot");

        assertThat(result.getOrigin()).isEqualTo("Delhi");
        assertThat(result.getDestination()).isEqualTo("Prayagraj");
        assertThat(result.getOptimizeFor()).isEqualTo("COST");
        assertThat(result.getAutonomyMode()).isEqualTo("FULL_AUTOPILOT");
        assertThat(result.getArrivalDeadline()).isNull();
    }

    @Test
    void parsesBatteryPercentageBeforeTheBatteryLabel() {
        JourneyIntentParseResponse result = parser.parse(
                "Take me from Kanpur to Bhopal with 76% battery, keep 10% reserve, "
                        + "maximum charging budget Rs 1500, arrive by 10:00 PM, fastest, full autopilot");

        assertThat(result.getCurrentBatteryPercent()).isEqualTo(76);
        assertThat(result.getMinimumArrivalBatteryPercent()).isEqualTo(10);
        assertThat(result.getRecognizedFields()).contains("currentBatteryPercent");
    }

    @Test
    void understandsTheOptimizationDescriptionShownInTheUi() {
        JourneyIntentParseResponse result = parser.parse(
                "Plan from Kanpur to Bhopal with 50% battery, arrive with at least 10%, "
                        + "max charging budget ₹2500, minimize total trip time, and ask before actions.");

        assertThat(result.getOptimizeFor()).isEqualTo("TIME");
        assertThat(result.getAutonomyMode()).isEqualTo("ASK_BEFORE_ACTIONS");
        assertThat(result.getRecognizedFields()).contains("optimizeFor", "autonomyMode");
    }

    @Test
    void leavesMissingOrInvalidConstraintsUnset() {
        JourneyIntentParseResponse result = parser.parse("Drive from Pune to Mumbai with 2% reserve");

        assertThat(result.getOrigin()).isEqualTo("Pune");
        assertThat(result.getDestination()).isEqualTo("Mumbai");
        assertThat(result.getMinimumArrivalBatteryPercent()).isNull();
        assertThat(result.getMaximumChargingBudget()).isNull();
    }
}
