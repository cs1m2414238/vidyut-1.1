package com.vidyut.agent.controller;

import com.vidyut.agent.dto.AgentWorkQueueResponse;
import com.vidyut.agent.service.AgentWorkQueueService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/work-queue")
@RequiredArgsConstructor
public class AgentWorkQueueController {
    private final AgentWorkQueueService workQueueService;
    private final CurrentUserUtil currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<AgentWorkQueueResponse>> queue() {
        return ResponseEntity.ok(ApiResponse.success(
                workQueueService.queue(currentUser.getCurrentAccountId(), currentUser.getCurrentMode())));
    }
}
