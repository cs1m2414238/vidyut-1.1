package com.vidyut.agent.dto;

import com.vidyut.autopilot.dto.AutopilotTripRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvAgentChatRequest {

    @NotBlank(message = "Agent message is required")
    @Size(max = 4000, message = "Agent message must be 4000 characters or fewer")
    private String message;

    private String sessionId;

    private String requestId;

    @Valid
    private AutopilotTripRequest tripContext;

    @Builder.Default
    private String workspace = "EV_OWNER";

    private Map<String, Object> groundingContext;
}
