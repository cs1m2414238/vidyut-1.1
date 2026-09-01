package com.vidyut.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.dto.AutopilotTripRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvAgentChatRequestTest {

    @Test
    void forwardsStructuredTripContextToThePythonAgent() throws Exception {
        EvAgentChatRequest request = EvAgentChatRequest.builder()
                .message("Plan this trip")
                .requestId("request-12345678")
                .tripContext(AutopilotTripRequest.builder()
                        .vehicleId(42L)
                        .origin("Ujjain")
                        .destination("Bhopal")
                        .currentBatteryPercent(80)
                        .minimumArrivalBatteryPercent(15)
                        .maximumChargingBudget(500)
                        .build())
                .build();

        JsonNode json = new ObjectMapper().valueToTree(request);

        assertThat(json.path("tripContext").path("vehicleId").asLong()).isEqualTo(42L);
        assertThat(json.path("tripContext").path("origin").asText()).isEqualTo("Ujjain");
        assertThat(json.path("tripContext").path("destination").asText()).isEqualTo("Bhopal");
        assertThat(json.path("tripContext").path("maximumChargingBudget").asDouble()).isEqualTo(500);
    }

    @Test
    void serializesRoleScopedGroundingContext() {
        EvAgentChatRequest request = EvAgentChatRequest.builder()
                .message("Explain the network fault")
                .workspace("COMPANY")
                .groundingContext(Map.of("faults", 1, "deterministicAnswer", "KNP-03 needs service."))
                .build();

        JsonNode json = new ObjectMapper().valueToTree(request);

        assertThat(json.path("workspace").asText()).isEqualTo("COMPANY");
        assertThat(json.path("groundingContext").path("faults").asInt()).isEqualTo(1);
        assertThat(json.path("groundingContext").path("deterministicAnswer").asText())
                .isEqualTo("KNP-03 needs service.");
    }
}
