package com.vidyut.agent.service;

import com.vidyut.agent.dto.EvAgentChatRequest;
import com.vidyut.agent.dto.EvAgentChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleScopedAgentService {

    private final AiAgentGateway agentGateway;

    public GroundedReply explain(String authorization, String workspace, Long accountId,
            String question, String deterministicAnswer, Map<String, Object> authoritativeContext) {
        if (authorization == null || authorization.isBlank()) {
            return deterministic(workspace, deterministicAnswer);
        }

        Map<String, Object> grounding = new LinkedHashMap<>();
        if (authoritativeContext != null) grounding.putAll(authoritativeContext);
        grounding.put("deterministicAnswer", deterministicAnswer);
        grounding.put("workspace", workspace);
        grounding.put("scope", "Authenticated account " + accountId + " only");

        String prefix = workspace.toLowerCase(Locale.ROOT);
        EvAgentChatRequest request = EvAgentChatRequest.builder()
                .message(question)
                .sessionId(prefix + "-account-" + accountId)
                .requestId(prefix + "-" + UUID.randomUUID())
                .workspace(workspace)
                .groundingContext(grounding)
                .build();
        try {
            EvAgentChatResponse response = agentGateway.chat(authorization, request);
            if (response.getReply() == null || response.getReply().isBlank()) {
                return deterministic(workspace, deterministicAnswer);
            }
            String provider = response.getProvider() == null ? "MODEL" : response.getProvider();
            return new GroundedReply(response.getReply(), response.getModel(), provider,
                    "DETERMINISTIC".equalsIgnoreCase(provider));
        } catch (RuntimeException exception) {
            log.warn("{} agent unavailable for account {}; using deterministic answer ({})",
                    workspace, accountId, exception.getClass().getSimpleName());
            return deterministic(workspace, deterministicAnswer);
        }
    }

    private GroundedReply deterministic(String workspace, String answer) {
        return new GroundedReply(answer, "deterministic-" + workspace.toLowerCase(Locale.ROOT) + "-fallback",
                "DETERMINISTIC", true);
    }

    public record GroundedReply(String answer, String model, String provider, boolean deterministicFallback) {}
}
