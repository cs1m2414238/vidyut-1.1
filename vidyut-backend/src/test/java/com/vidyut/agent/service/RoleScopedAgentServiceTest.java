package com.vidyut.agent.service;

import com.vidyut.agent.dto.EvAgentChatRequest;
import com.vidyut.agent.dto.EvAgentChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleScopedAgentServiceTest {

    private final AiAgentGateway gateway = mock(AiAgentGateway.class);
    private final RoleScopedAgentService service = new RoleScopedAgentService(gateway);

    @Test
    void forwardsOnlyGroundedRoleContextAndReturnsModelMetadata() {
        when(gateway.chat(eq("Bearer test-token"), any())).thenReturn(EvAgentChatResponse.builder()
                .reply("KNP-03 is the highest repair priority.")
                .model("gemini-3.5-flash")
                .provider("GEMINI")
                .build());

        RoleScopedAgentService.GroundedReply reply = service.explain(
                "Bearer test-token", "HOST", 17L, "Which charger needs service?",
                "The rules answer.", Map.of("chargerCode", "KNP-03", "riskScore", 72));

        assertThat(reply.answer()).isEqualTo("KNP-03 is the highest repair priority.");
        assertThat(reply.provider()).isEqualTo("GEMINI");
        assertThat(reply.deterministicFallback()).isFalse();
        ArgumentCaptor<EvAgentChatRequest> request = ArgumentCaptor.forClass(EvAgentChatRequest.class);
        verify(gateway).chat(eq("Bearer test-token"), request.capture());
        assertThat(request.getValue().getWorkspace()).isEqualTo("HOST");
        assertThat(request.getValue().getGroundingContext()).containsEntry("chargerCode", "KNP-03")
                .containsEntry("deterministicAnswer", "The rules answer.");
        assertThat(request.getValue().getGroundingContext().toString()).doesNotContain("test-token");
    }

    @Test
    void degradesToDeterministicAnswerWhenAgentIsOffline() {
        when(gateway.chat(any(), any())).thenThrow(new RuntimeException("offline"));

        RoleScopedAgentService.GroundedReply reply = service.explain(
                "Bearer test-token", "COMPANY", 22L, "How is the network?",
                "Three chargers are available.", Map.of("available", 3));

        assertThat(reply.answer()).isEqualTo("Three chargers are available.");
        assertThat(reply.model()).isEqualTo("deterministic-company-fallback");
        assertThat(reply.provider()).isEqualTo("DETERMINISTIC");
        assertThat(reply.deterministicFallback()).isTrue();
    }
}
