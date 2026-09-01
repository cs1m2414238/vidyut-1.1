package com.vidyut.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.agent.dto.EvAgentChatRequest;
import com.vidyut.agent.dto.EvAgentChatResponse;
import com.vidyut.agent.service.AiAgentGateway;
import com.vidyut.common.exception.AgentServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiAgentGatewayTest {

    private MockRestServiceServer server;
    private AiAgentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new AiAgentGateway(builder, new ObjectMapper(), "http://agent.test");
    }

    @Test
    void forwardsUserJwtAndMapsAgentResponse() {
        server.expect(once(), requestTo("http://agent.test/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ev-mode-token"))
                .andExpect(content().json("""
                        {"message":"Plan my trip","sessionId":"session-1234","requestId":"request-1234"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "sessionId":"session-1234",
                          "requestId":"request-1234",
                          "reply":"Your charger is reserved.",
                          "model":"gemini-3.6-flash",
                          "toolCalls":[{"name":"plan_and_reserve_trip","status":"completed"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        EvAgentChatResponse response = gateway.chat(
                "Bearer ev-mode-token",
                EvAgentChatRequest.builder()
                        .message("Plan my trip")
                        .sessionId("session-1234")
                        .requestId("request-1234")
                        .build()
        );

        assertThat(response.getReply()).isEqualTo("Your charger is reserved.");
        assertThat(response.getModel()).isEqualTo("gemini-3.6-flash");
        assertThat(response.getToolCalls()).singleElement()
                .extracting("name", "status")
                .containsExactly("plan_and_reserve_trip", "completed");
        server.verify();
    }

    @Test
    void mapsAgentServerFailureToServiceUnavailable() {
        server.expect(requestTo("http://agent.test/v1/chat"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.chat(
                "Bearer ev-mode-token",
                EvAgentChatRequest.builder().message("Hello agent").build()
        )).isInstanceOf(AgentServiceUnavailableException.class);
        server.verify();
    }
}
