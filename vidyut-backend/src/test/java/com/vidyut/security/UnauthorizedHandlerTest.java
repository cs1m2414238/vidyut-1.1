package com.vidyut.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class UnauthorizedHandlerTest {

    @Test
    void writesAJsonErrorInsteadOfDelegatingToTheTomcatHtmlPage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UnauthorizedHandler handler = new UnauthorizedHandler(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("invalid token")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.path("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("message").asText()).contains("expired");
        assertThat(body.path("details").isArray()).isTrue();
    }
}
