package com.vidyut.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.AccessMode;
import com.vidyut.agent.dto.AgentWorkQueueResponse;
import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkItem;
import com.vidyut.agent.entity.AgentWorkPriority;
import com.vidyut.agent.entity.AgentWorkStatus;
import com.vidyut.agent.repository.AgentWorkItemRepository;
import com.vidyut.company.dto.CompanyAgentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentWorkQueueService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentWorkItemRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AgentWorkQueueResponse queue(Long accountId, AccessMode mode) {
        AgentWorkspace workspace = AgentWorkspace.from(mode);
        List<AgentWorkItem> items = repository.findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(accountId, workspace);
        EnumMap<AgentWorkStatus, Long> counts = new EnumMap<>(AgentWorkStatus.class);
        for (AgentWorkStatus status : AgentWorkStatus.values()) counts.put(status, 0L);
        items.forEach(item -> counts.compute(item.getStatus(), (ignored, count) -> count == null ? 1 : count + 1));
        return new AgentWorkQueueResponse(workspace, counts, items.stream().map(this::response).toList(), LocalDateTime.now());
    }

    @Transactional
    public AgentWorkItem upsert(Long accountId, AgentWorkspace workspace, String workKey,
            String category, AgentWorkStatus status, AgentWorkPriority priority,
            String title, String detail, String actionType, Map<String, Object> actionPayload,
            String resourceType, Long resourceId, Long sourceEventId) {
        AgentWorkItem item = repository.findByAccountIdAndWorkspaceAndWorkKey(accountId, workspace, workKey)
                .orElseGet(() -> AgentWorkItem.builder()
                        .accountId(accountId).workspace(workspace).workKey(limit(workKey, 180)).build());
        item.setCategory(limit(category, 60));
        item.setStatus(status);
        item.setPriority(priority);
        item.setTitle(limit(title, 180));
        item.setDetail(limit(detail, 1500));
        item.setActionType(limitNullable(actionType, 80));
        item.setActionPayloadJson(writePayload(actionPayload));
        item.setResourceType(limitNullable(resourceType, 50));
        item.setResourceId(resourceId);
        if (sourceEventId != null) item.setSourceEventId(sourceEventId);
        item.setResultSummary(null);
        return repository.save(item);
    }

    @Transactional
    public void captureCompanyPlan(Long accountId, List<CompanyAgentResponse.RecommendedAction> actions) {
        for (CompanyAgentResponse.RecommendedAction action : actions) {
            Long resourceId = action.chargerId() != null ? action.chargerId() : action.stationId();
            String resourceType = action.chargerId() != null ? "CONNECTOR" : action.stationId() != null ? "STATION" : "COMPANY";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", action.action().name());
            payload.put("chargerId", action.chargerId());
            payload.put("stationId", action.stationId());
            payload.put("proposedPricePerKwh", action.proposedPricePerKwh());
            payload.put("reason", action.reason());
            payload.put("expectedStatus", action.expectedStatus() == null ? null : action.expectedStatus().name());
            upsert(accountId, AgentWorkspace.COMPANY,
                    actionKey(AgentWorkspace.COMPANY, action.action().name(), resourceType, resourceId),
                    "OPERATION", action.requiresApproval() ? AgentWorkStatus.NEEDS_APPROVAL : AgentWorkStatus.IN_PROGRESS,
                    priority(action.risk()), action.label(), action.reason(), action.action().name(), payload,
                    resourceType, resourceId, null);
        }
    }

    @Transactional
    public void captureHostPlan(Long accountId, List<Map<String, Object>> actions) {
        for (Map<String, Object> action : actions) {
            if (!Boolean.TRUE.equals(action.get("requiresConfirmation"))) continue;
            String actionType = Objects.toString(action.get("action"), "HOST_ACTION");
            Long connectorId = number(action.get("connectorId"));
            Long stationId = number(action.get("stationId"));
            Long propertyId = number(action.get("propertyId"));
            Long resourceId = connectorId != null ? connectorId : stationId != null ? stationId : propertyId;
            String resourceType = connectorId != null ? "CONNECTOR" : stationId != null ? "STATION"
                    : propertyId != null ? "PROPERTY" : "HOST";
            upsert(accountId, AgentWorkspace.HOST,
                    actionKey(AgentWorkspace.HOST, actionType, resourceType, resourceId),
                    "PROPERTY_OPERATION", AgentWorkStatus.NEEDS_APPROVAL,
                    actionType.contains("SERVICE") || actionType.contains("MAINTENANCE")
                            ? AgentWorkPriority.HIGH : AgentWorkPriority.MEDIUM,
                    Objects.toString(action.get("label"), "Prepared Host action"),
                    Objects.toString(action.get("detail"), "Review the prepared action."),
                    actionType, action, resourceType, resourceId, null);
        }
    }

    @Transactional
    public void completeAction(Long accountId, AgentWorkspace workspace, String actionType,
            String resourceType, Long resourceId, String resultSummary) {
        String key = actionKey(workspace, actionType, resourceType, resourceId);
        repository.findByAccountIdAndWorkspaceAndWorkKey(accountId, workspace, key).ifPresent(item -> {
            item.setStatus(AgentWorkStatus.DONE);
            item.setResultSummary(limit(resultSummary, 1500));
            repository.save(item);
        });
    }

    @Transactional
    public void completeResource(String resourceType, Long resourceId, String resultSummary) {
        repository.findByResourceTypeAndResourceIdAndStatusIn(resourceType, resourceId,
                        List.of(AgentWorkStatus.IN_PROGRESS, AgentWorkStatus.NEEDS_APPROVAL, AgentWorkStatus.ATTENTION))
                .forEach(item -> {
                    item.setStatus(AgentWorkStatus.DONE);
                    item.setResultSummary(limit(resultSummary, 1500));
                    repository.save(item);
                });
    }

    public static String actionKey(AgentWorkspace workspace, String actionType, String resourceType, Long resourceId) {
        return (workspace + ":action:" + actionType + ":" + resourceType + ":" + Objects.toString(resourceId, "none"))
                .toLowerCase(Locale.ROOT);
    }

    private AgentWorkQueueResponse.WorkItem response(AgentWorkItem item) {
        return new AgentWorkQueueResponse.WorkItem(item.getId(), item.getCategory(), item.getStatus(), item.getPriority(),
                item.getTitle(), item.getDetail(), item.getActionType(), readPayload(item.getActionPayloadJson()),
                item.getResourceType(), item.getResourceId(), item.getResultSummary(), item.getCreatedAt(),
                item.getUpdatedAt(), item.getCompletedAt());
    }

    private AgentWorkPriority priority(String value) {
        try {
            return AgentWorkPriority.valueOf(Objects.toString(value, "MEDIUM").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AgentWorkPriority.MEDIUM;
        }
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            return limit(objectMapper.writeValueAsString(payload), 5000);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to persist the prepared agent action", exception);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        if (payload == null || payload.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String limitNullable(String value, int max) {
        return value == null ? null : limit(value, max);
    }

    private String limit(String value, int max) {
        String safe = Objects.toString(value, "").trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
