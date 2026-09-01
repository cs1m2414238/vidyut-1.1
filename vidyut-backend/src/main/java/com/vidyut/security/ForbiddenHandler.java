package com.vidyut.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.common.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ForbiddenHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public ForbiddenHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of("This token is not authorized for the requested mode",
                        "FORBIDDEN", List.of()));
    }
}
