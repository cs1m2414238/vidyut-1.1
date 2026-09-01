package com.vidyut.agent.controller;

import com.vidyut.agent.dto.AgentActionResponse;
import com.vidyut.agent.dto.CongestionAnalysisResponse;
import com.vidyut.agent.service.CongestionAgentService;
import com.vidyut.agent.service.MaintenanceAgentService;
import com.vidyut.agent.service.VidyutAgentService;
import com.vidyut.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final VidyutAgentService vidyutAgentService;
    private final CongestionAgentService congestionAgentService;
    private final MaintenanceAgentService maintenanceAgentService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AgentActionResponse>> getAgentStatus() {
        return ResponseEntity.ok(ApiResponse.success(vidyutAgentService.getAgentStatus()));
    }

    @GetMapping("/congestion")
    public ResponseEntity<ApiResponse<CongestionAnalysisResponse>> analyzeCongestion(@RequestParam(required = false, defaultValue = "Gomti Nagar") String area) {
        return ResponseEntity.ok(ApiResponse.success(congestionAgentService.analyzeCongestion(area)));
    }

    @GetMapping("/maintenance/{stationId}")
    public ResponseEntity<ApiResponse<AgentActionResponse>> inspectStationHealth(@PathVariable Long stationId) {
        return ResponseEntity.ok(ApiResponse.success(maintenanceAgentService.inspectStationHealth(stationId)));
    }
}
