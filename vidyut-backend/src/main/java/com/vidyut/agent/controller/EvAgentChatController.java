package com.vidyut.agent.controller;

import com.vidyut.agent.dto.EvAgentChatRequest;
import com.vidyut.agent.dto.EvAgentChatResponse;
import com.vidyut.agent.service.AiAgentGateway;
import com.vidyut.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ev/agent")
@RequiredArgsConstructor
public class EvAgentChatController {

    private final AiAgentGateway aiAgentGateway;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<EvAgentChatResponse>> chat(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody EvAgentChatRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Vidyut AI agent completed the request",
                aiAgentGateway.chat(authorization, request)
        ));
    }
}
