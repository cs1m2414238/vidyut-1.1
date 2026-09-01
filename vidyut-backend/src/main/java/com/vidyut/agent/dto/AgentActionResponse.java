package com.vidyut.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActionResponse {
    private String agentName;
    private String status;
    private String actionTaken;
    private String insights;
}
