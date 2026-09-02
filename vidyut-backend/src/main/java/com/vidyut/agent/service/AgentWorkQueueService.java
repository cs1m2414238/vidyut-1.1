package com.vidyut.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.AccessMode;
import com.vidyut.agent.dto.AgentActivityResponse;
import com.vidyut.agent.dto.AgentWorkQueueResponse;
import com.vidyut.agent.entity.AgentActivity;
import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkItem;
import com.vidyut.agent.entity.AgentWorkPriority;
import com.vidyut.agent.entity.AgentWorkStatus;
import com.vidyut.agent.repository.AgentActivityRepository;
import com.vidyut.agent.repository.AgentWorkItemRepository;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.company.dto.CompanyAgentResponse;
import com.vidyut.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentWorkQueueService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Duration APPROVAL_TTL = Duration.ofMinutes(15);

    private final AgentWorkItemRepository repository;
    private final AgentActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    public record WorkExplanation(String whatHappened, String whyItMatters, String alreadyDone,
                                  String proposedAction, String approvalReason, String expectedImpact) {}

    public record ExecutionLease(AgentWorkItem item, boolean duplicate, boolean stale,
                                 Map<String, Object> previousResult) {}

    @Transactional(readOnly = true)
    public AgentWorkQueueResponse queue(Long accountId, AccessMode mode) {
        AgentWorkspace workspace = AgentWorkspace.from(mode);
        List<AgentWorkItem> items = repository.findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(accountId, workspace);
        EnumMap<AgentWorkStatus, Long> counts = new EnumMap<>(AgentWorkStatus.class);
        for (AgentWorkStatus status : AgentWorkStatus.values()) counts.put(status, 0L);
        items.forEach(item -> counts.compute(item.getStatus(), (ignored, count) -> count == null ? 1 : count + 1));
        return new AgentWorkQueueResponse(workspace, counts, items.stream().map(this::response).toList(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public AgentActivityResponse activity(Long accountId, AccessMode mode) {
        AgentWorkspace workspace = AgentWorkspace.from(mode);
        List<AgentActivityResponse.Activity> activities = activityRepository
                .findTop50ByAccountIdAndWorkspaceOrderByOccurredAtDesc(accountId, workspace).stream()
                .map(item -> new AgentActivityResponse.Activity(item.getId(), item.getWorkItemId(),
                        item.getCorrelationId(), item.getActivityType(), item.getSummary(), item.getDetail(),
                        readPayload(item.getMetadataJson()), item.getOccurredAt()))
                .toList();
        return new AgentActivityResponse(workspace, activities, LocalDateTime.now());
    }

    @Transactional
    public AgentWorkItem upsert(Long accountId, AgentWorkspace workspace, String workKey,
            String category, AgentWorkStatus status, AgentWorkPriority priority,
            String title, String detail, String actionType, Map<String, Object> actionPayload,
            String resourceType, Long resourceId, Long sourceEventId) {
        String correlationId = sourceEventId == null
                ? "work-" + stableHash(accountId + ":" + workspace + ":" + workKey)
                : "event-" + sourceEventId;
        return upsertTraced(accountId, workspace, workKey, category, status, priority, title, detail,
                actionType, actionPayload, resourceType, resourceId, sourceEventId, correlationId,
                "objective-" + stableHash(accountId + ":" + workspace + ":" + category), null,
                defaultExplanation(title, detail, actionType, status));
    }

    @Transactional
    public AgentWorkItem upsertTraced(Long accountId, AgentWorkspace workspace, String workKey,
            String category, AgentWorkStatus status, AgentWorkPriority priority,
            String title, String detail, String actionType, Map<String, Object> actionPayload,
            String resourceType, Long resourceId, Long sourceEventId, String correlationId,
            String objectiveId, String actionBundleId, WorkExplanation explanation) {
        String boundedWorkKey = limit(workKey, 180);
        String payloadJson = writePayload(actionPayload);
        String idempotencyKey = "agent-" + stableHash(accountId + ":" + workspace + ":" + boundedWorkKey
                + ":" + Objects.toString(payloadJson, ""));
        AgentWorkItem item = repository.findByAccountIdAndWorkspaceAndWorkKey(accountId, workspace, boundedWorkKey)
                .orElse(null);
        while (item != null && terminal(item.getStatus())) {
            boundedWorkKey = revisionKey(boundedWorkKey, item.getId());
            idempotencyKey = "agent-" + stableHash(accountId + ":" + workspace + ":" + boundedWorkKey
                    + ":" + Objects.toString(payloadJson, ""));
            item = repository.findByAccountIdAndWorkspaceAndWorkKey(accountId, workspace, boundedWorkKey).orElse(null);
        }
        boolean created = item == null;
        if (created) {
            item = AgentWorkItem.builder().accountId(accountId).workspace(workspace).workKey(boundedWorkKey)
                    .idempotencyKey(idempotencyKey).correlationId(limit(correlationId, 100)).build();
        } else if (!Objects.equals(item.getIdempotencyKey(), idempotencyKey)) {
            // A changed plan invalidates every previously issued approval token.
            item.setIdempotencyKey(idempotencyKey);
            item.setApprovedAt(null);
            item.setExecutionStartedAt(null);
            item.setExecutedAt(null);
            item.setRequestId(null);
        }
        LocalDateTime now = LocalDateTime.now();
        item.setCategory(limit(category, 60));
        item.setStatus(status);
        item.setPriority(priority);
        item.setTitle(limit(title, 180));
        item.setDetail(limit(detail, 1500));
        item.setWhatHappened(limit(explanation.whatHappened(), 1000));
        item.setWhyItMatters(limit(explanation.whyItMatters(), 1000));
        item.setAlreadyDone(limit(explanation.alreadyDone(), 1000));
        item.setProposedAction(limit(explanation.proposedAction(), 1000));
        item.setApprovalReason(limitNullable(explanation.approvalReason(), 1000));
        item.setExpectedImpact(limit(explanation.expectedImpact(), 1000));
        item.setActionType(limitNullable(actionType, 80));
        item.setActionPayloadJson(payloadJson);
        item.setExpectedStateJson(writePayload(expectedState(actionPayload)));
        item.setResourceType(limitNullable(resourceType, 50));
        item.setResourceId(resourceId);
        item.setObjectiveId(limitNullable(objectiveId, 100));
        item.setActionBundleId(limitNullable(actionBundleId, 100));
        if (sourceEventId != null) item.setSourceEventId(sourceEventId);
        if (!terminal(status)) {
            item.setResultSummary(null);
            item.setExecutionResultJson(null);
            item.setFailureReason(null);
            item.setCompletedAt(null);
        }
        item.setPreparedAt(now);
        item.setExpiresAt(requiresFreshApproval(status, actionType) ? now.plus(APPROVAL_TTL) : null);
        AgentWorkItem saved = repository.save(item);
        Map<String, Object> persistedPayload = new LinkedHashMap<>(actionPayload == null ? Map.of() : actionPayload);
        persistedPayload.put("workItemId", saved.getId());
        persistedPayload.put("idempotencyKey", saved.getIdempotencyKey());
        persistedPayload.put("correlationId", saved.getCorrelationId());
        saved.setActionPayloadJson(writePayload(persistedPayload));
        saved = repository.save(saved);
        if (created) {
            recordActivity(saved, "WORK_PREPARED", "Prepared: " + saved.getTitle(), saved.getAlreadyDone(), Map.of());
        }
        return saved;
    }

    @Transactional
    public List<AgentWorkItem> captureCompanyPlan(Long accountId, List<CompanyAgentResponse.RecommendedAction> actions) {
        return actions.stream().map(action -> {
            Long resourceId = action.chargerId() != null ? action.chargerId() : action.stationId();
            String resourceType = action.chargerId() != null ? "CONNECTOR" : action.stationId() != null ? "STATION" : "COMPANY";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", action.action().name());
            payload.put("chargerId", action.chargerId());
            payload.put("stationId", action.stationId());
            payload.put("proposedPricePerKwh", action.proposedPricePerKwh());
            payload.put("reason", action.reason());
            payload.put("expectedStatus", action.expectedStatus() == null ? null : action.expectedStatus().name());
            payload.put("expectedPricePerKwh", action.expectedPricePerKwh());
            return upsertTraced(accountId, AgentWorkspace.COMPANY,
                    actionKey(AgentWorkspace.COMPANY, action.action().name(), resourceType, resourceId),
                    "OPERATION", action.requiresApproval() ? AgentWorkStatus.NEEDS_APPROVAL : AgentWorkStatus.PREPARED,
                    priority(action.risk()), action.label(), action.reason(), action.action().name(), payload,
                    resourceType, resourceId, null, nullSafeCorrelation(accountId, AgentWorkspace.COMPANY,
                            action.action().name(), resourceId), "company-network-operations", null,
                    new WorkExplanation("Vidyut detected an actionable network condition.", action.reason(),
                            "It inspected current ownership, connector health, bookings, and saved autonomy policy.",
                            action.label(), action.requiresApproval()
                                    ? "This action changes shared operational state and is outside automatic authority." : null,
                            expectedImpact(action.action().name())));
        }).toList();
    }

    @Transactional
    public List<AgentWorkItem> captureHostPlan(Long accountId, List<Map<String, Object>> actions) {
        return actions.stream().filter(action -> Boolean.TRUE.equals(action.get("requiresConfirmation"))).map(action -> {
            String actionType = Objects.toString(action.get("action"), "HOST_ACTION");
            Long connectorId = number(action.get("connectorId"));
            Long stationId = number(action.get("stationId"));
            Long propertyId = number(action.get("propertyId"));
            Long resourceId = connectorId != null ? connectorId : stationId != null ? stationId : propertyId;
            String resourceType = connectorId != null ? "CONNECTOR" : stationId != null ? "STATION"
                    : propertyId != null ? "PROPERTY" : "HOST";
            return upsertTraced(accountId, AgentWorkspace.HOST,
                    actionKey(AgentWorkspace.HOST, actionType, resourceType, resourceId),
                    "PROPERTY_OPERATION", AgentWorkStatus.NEEDS_APPROVAL,
                    actionType.contains("SERVICE") || actionType.contains("MAINTENANCE")
                            ? AgentWorkPriority.HIGH : AgentWorkPriority.MEDIUM,
                    Objects.toString(action.get("label"), "Prepared Host action"),
                    Objects.toString(action.get("detail"), "Review the prepared action."),
                    actionType, action, resourceType, resourceId, null,
                    nullSafeCorrelation(accountId, AgentWorkspace.HOST, actionType, resourceId),
                    "host-property-operations", null,
                    new WorkExplanation("Vidyut found a property or hosted-equipment action to review.",
                            Objects.toString(action.get("detail"), "The action affects a Host-owned resource."),
                            "It verified the resource is visible inside this Host account.",
                            Objects.toString(action.get("label"), "Execute the prepared Host action."),
                            "Host approval is required before any property or equipment mutation.",
                            expectedImpact(actionType)));
        }).toList();
    }

    @Transactional
    public ExecutionLease beginExecution(Long accountId, AgentWorkspace workspace, Long workItemId,
            String suppliedIdempotencyKey, String actionType, String resourceType, Long resourceId,
            boolean explicitlyApproved) {
        String workKey = actionKey(workspace, actionType, resourceType, resourceId);
        AgentWorkItem item = workItemId != null
                ? repository.findOwnedForUpdate(workItemId, accountId, workspace).orElseThrow(
                        () -> new BadRequestException("Prepared agent action was not found for this account"))
                : repository.findByWorkKeyForUpdate(accountId, workspace, workKey).orElseThrow(
                        () -> new BadRequestException("This action is not backed by a current prepared work item"));
        if (suppliedIdempotencyKey != null && !suppliedIdempotencyKey.isBlank()
                && !Objects.equals(item.getIdempotencyKey(), suppliedIdempotencyKey)) {
            markStale(item, "The approval token does not match the latest prepared action.");
            return new ExecutionLease(item, false, true, Map.of());
        }
        if (!Objects.equals(item.getActionType(), actionType)
                || !Objects.equals(item.getResourceType(), resourceType)
                || !Objects.equals(item.getResourceId(), resourceId)) {
            markStale(item, "The approved action target no longer matches the prepared work item.");
            return new ExecutionLease(item, false, true, Map.of());
        }
        if (item.getStatus() == AgentWorkStatus.COMPLETED || item.getStatus() == AgentWorkStatus.DONE) {
            recordActivity(item, "DUPLICATE_SUPPRESSED", "Duplicate execution suppressed",
                    "The idempotency key already has a verified result; no mutation ran again.", Map.of());
            return new ExecutionLease(item, true, false, readPayload(item.getExecutionResultJson()));
        }
        if (item.getStatus() == AgentWorkStatus.STALE || item.getStatus() == AgentWorkStatus.CANCELLED
                || item.getStatus() == AgentWorkStatus.FAILED) {
            return new ExecutionLease(item, false, true, Map.of());
        }
        LocalDateTime now = LocalDateTime.now();
        if (item.getExpiresAt() != null && !now.isBefore(item.getExpiresAt())) {
            markStale(item, "Approval expired before execution. Ask Vidyut to regenerate this action from current state.");
            return new ExecutionLease(item, false, true, Map.of());
        }
        if (explicitlyApproved) {
            item.setStatus(AgentWorkStatus.APPROVED);
            item.setApprovedAt(now);
            recordActivity(item, "ACTION_APPROVED", "Approval recorded", item.getApprovalReason(), Map.of());
        }
        item.setStatus(AgentWorkStatus.EXECUTING);
        item.setExecutionStartedAt(now);
        item.setRequestId("request-" + UUID.randomUUID());
        item.setFailureReason(null);
        repository.save(item);
        recordActivity(item, "EXECUTION_STARTED", "Execution started",
                "Vidyut revalidates live ownership, permissions, and resource state before committing changes.", Map.of());
        return new ExecutionLease(item, false, false, Map.of());
    }

    @Transactional
    public void completeExecution(AgentWorkItem item, String resultSummary, Map<String, Object> result) {
        item.setStatus(AgentWorkStatus.COMPLETED);
        item.setResultSummary(limit(resultSummary, 1500));
        item.setExecutionResultJson(writePayload(result));
        item.setFailureReason(null);
        item.setExecutedAt(LocalDateTime.now());
        repository.save(item);
        recordActivity(item, "EXECUTION_VERIFIED", "Completed and verified", resultSummary,
                result == null ? Map.of() : result);
    }

    @Transactional
    public void markStale(AgentWorkItem item, String reason) {
        item.setStatus(AgentWorkStatus.STALE);
        item.setFailureReason(limit(reason, 1500));
        repository.save(item);
        recordActivity(item, "APPROVAL_STALE", "Approval became stale", reason, Map.of());
    }

    @Transactional
    public void recordFailure(AgentWorkItem item, String reason, boolean retryable) {
        item.setRetryCount(item.getRetryCount() + 1);
        boolean terminalFailure = !retryable || item.getRetryCount() >= item.getMaxRetries();
        item.setStatus(terminalFailure ? AgentWorkStatus.FAILED : AgentWorkStatus.RETRYABLE_FAILURE);
        item.setFailureReason(limit(reason, 1500));
        repository.save(item);
        recordActivity(item, terminalFailure ? "EXECUTION_FAILED" : "EXECUTION_RETRY_SCHEDULED",
                terminalFailure ? "Execution failed" : "Execution will be retried", reason,
                Map.of("attempt", item.getRetryCount(), "maxAttempts", item.getMaxRetries()));
    }

    @Transactional
    public void block(AgentWorkItem item, String reason) {
        item.setStatus(AgentWorkStatus.BLOCKED);
        item.setFailureReason(limit(reason, 1500));
        repository.save(item);
        recordActivity(item, "EXECUTION_BLOCKED", "Action blocked by current state", reason, Map.of());
    }

    @Transactional
    public void completeAction(Long accountId, AgentWorkspace workspace, String actionType,
            String resourceType, Long resourceId, String resultSummary) {
        String key = actionKey(workspace, actionType, resourceType, resourceId);
        repository.findByAccountIdAndWorkspaceAndWorkKey(accountId, workspace, key).ifPresent(item ->
                completeExecution(item, resultSummary, Map.of("message", resultSummary)));
    }

    @Transactional
    public void completeResource(String resourceType, Long resourceId, String resultSummary) {
        repository.findByResourceTypeAndResourceIdAndStatusIn(resourceType, resourceId,
                        List.of(AgentWorkStatus.PENDING, AgentWorkStatus.PREPARED, AgentWorkStatus.IN_PROGRESS,
                                AgentWorkStatus.NEEDS_APPROVAL, AgentWorkStatus.APPROVED,
                                AgentWorkStatus.EXECUTING, AgentWorkStatus.ATTENTION,
                                AgentWorkStatus.RETRYABLE_FAILURE, AgentWorkStatus.BLOCKED))
                .forEach(item -> completeExecution(item, resultSummary, Map.of("message", resultSummary)));
    }

    @Transactional
    public void trackJourney(AutopilotTrip trip, AgentWorkStatus status, String detail) {
        upsertTraced(trip.getUserId(), AgentWorkspace.EV, "ev:journey:" + trip.getId(), "JOURNEY_OPERATION",
                status, status == AgentWorkStatus.ATTENTION || status == AgentWorkStatus.BLOCKED
                        ? AgentWorkPriority.HIGH : AgentWorkPriority.MEDIUM,
                "Journey to " + trip.getDestination(), detail, null, Map.of("tripId", trip.getId()),
                "TRIP", trip.getId(), null, "journey-" + trip.getId(), "journey-" + trip.getId(), null,
                new WorkExplanation("Vidyut is monitoring the active journey.", detail,
                        "Route, charging stops, reserve, and journey state are persisted.",
                        "Continue monitoring and intervene only when constraints require it.", null,
                        "Protect arrival reserve while avoiding unnecessary driver interruption."));
    }

    @Transactional
    public void completeJourney(AutopilotTrip trip, String resultSummary) {
        repository.findByAccountIdAndWorkspaceAndWorkKey(trip.getUserId(), AgentWorkspace.EV,
                "ev:journey:" + trip.getId()).ifPresent(item -> completeExecution(item, resultSummary,
                Map.of("tripId", trip.getId(), "message", resultSummary)));
    }

    @Transactional
    public AgentWorkItem trackRecovery(AutopilotTrip trip, String incidentId, AgentWorkStatus status,
            String detail, Map<String, Object> payload) {
        Object failedConnectorId = payload == null ? null : payload.get("failedConnectorId");
        String correlationId = failedConnectorId instanceof Number number
                ? "connector-" + number.longValue()
                : "recovery-" + stableHash(trip.getId() + ":" + incidentId);
        return upsertTraced(trip.getUserId(), AgentWorkspace.EV, recoveryKey(trip.getId(), incidentId),
                "RECOVERY_ACTION_BUNDLE", status,
                status == AgentWorkStatus.ATTENTION || status == AgentWorkStatus.BLOCKED
                        ? AgentWorkPriority.CRITICAL : AgentWorkPriority.HIGH,
                "Protect the journey after a charger incident", detail, "RECOVERY_ACTION_BUNDLE", payload,
                "TRIP", trip.getId(), null, correlationId, "journey-" + trip.getId(),
                "recovery-bundle-" + stableHash(trip.getId() + ":" + incidentId),
                new WorkExplanation("The planned connector became unavailable during this journey.",
                        "The current reservation and route can no longer guarantee the saved arrival reserve.",
                        "Vidyut excluded the failed connector and evaluated complete remaining-journey alternatives.",
                        "Replace superseded reservations, reserve the validated connectors, and update navigation.",
                        status == AgentWorkStatus.NEEDS_APPROVAL
                                ? "Reservation replacement and navigation changes require driver approval." : null,
                        "Restore a verified route while preserving battery, budget, connector, and deadline constraints."));
    }

    @Transactional
    public ExecutionLease beginRecoveryExecution(AutopilotTrip trip, String incidentId, boolean driverApproval) {
        AgentWorkItem item = latestRecovery(trip, incidentId)
                .orElseThrow(() -> new BadRequestException("The recovery approval is not backed by a prepared work item"));
        return beginExecution(trip.getUserId(), AgentWorkspace.EV, item.getId(), item.getIdempotencyKey(),
                "RECOVERY_ACTION_BUNDLE", "TRIP", trip.getId(), driverApproval);
    }

    @Transactional
    public void completeRecovery(AutopilotTrip trip, String incidentId, String resultSummary) {
        latestRecovery(trip, incidentId).ifPresent(item -> completeExecution(item, resultSummary,
                Map.of("tripId", trip.getId(), "incidentId", incidentId, "message", resultSummary)));
    }

    @Transactional
    public void recordRecoveryActivity(AutopilotTrip trip, String incidentId, String type,
            String summary, String detail, Map<String, Object> metadata) {
        latestRecovery(trip, incidentId).ifPresent(item -> recordActivity(item, type, summary, detail, metadata));
    }

    public static String actionKey(AgentWorkspace workspace, String actionType, String resourceType, Long resourceId) {
        return (workspace + ":action:" + actionType + ":" + resourceType + ":" + Objects.toString(resourceId, "none"))
                .toLowerCase(Locale.ROOT);
    }

    private String recoveryKey(Long tripId, String incidentId) {
        return "ev:recovery:" + tripId + ":" + incidentId;
    }

    private java.util.Optional<AgentWorkItem> latestRecovery(AutopilotTrip trip, String incidentId) {
        String key = recoveryKey(trip.getId(), incidentId);
        return repository.findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(trip.getUserId(), AgentWorkspace.EV)
                .stream().filter(item -> item.getWorkKey().equals(key)
                        || item.getWorkKey().startsWith(key + ":revision-"))
                .filter(item -> !terminal(item.getStatus()))
                .findFirst()
                .or(() -> repository.findByAccountIdAndWorkspaceAndWorkKey(
                        trip.getUserId(), AgentWorkspace.EV, key));
    }

    private AgentWorkQueueResponse.WorkItem response(AgentWorkItem item) {
        return new AgentWorkQueueResponse.WorkItem(item.getId(), item.getIdempotencyKey(), item.getCorrelationId(),
                item.getObjectiveId(), item.getActionBundleId(), item.getRequestId(), item.getCategory(), item.getStatus(),
                item.getPriority(), item.getTitle(), item.getDetail(), item.getWhatHappened(), item.getWhyItMatters(),
                item.getAlreadyDone(), item.getProposedAction(), item.getApprovalReason(), item.getExpectedImpact(),
                item.getActionType(), readPayload(item.getActionPayloadJson()), item.getResourceType(), item.getResourceId(),
                item.getResultSummary(), item.getFailureReason(), item.getRetryCount(), item.getMaxRetries(),
                item.getCreatedAt(), item.getUpdatedAt(), item.getCompletedAt(), item.getPreparedAt(), item.getApprovedAt(),
                item.getExecutionStartedAt(), item.getExecutedAt(), item.getExpiresAt());
    }

    private void recordActivity(AgentWorkItem item, String type, String summary, String detail,
            Map<String, Object> metadata) {
        activityRepository.save(AgentActivity.builder().accountId(item.getAccountId()).workspace(item.getWorkspace())
                .workItemId(item.getId()).correlationId(item.getCorrelationId()).activityType(limit(type, 60))
                .summary(limit(summary, 240)).detail(limitNullable(detail, 1500))
                .metadataJson(writePayload(metadata)).build());
    }

    private WorkExplanation defaultExplanation(String title, String detail, String actionType, AgentWorkStatus status) {
        return new WorkExplanation(title, detail, "Vidyut captured this item from verified backend state.",
                actionType == null ? "Continue monitoring." : "Execute " + actionType.replace('_', ' ').toLowerCase(Locale.ROOT) + ".",
                status == AgentWorkStatus.NEEDS_APPROVAL ? "The action changes shared state and requires approval." : null,
                detail);
    }

    private Map<String, Object> expectedState(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (key.startsWith("expected") || key.equals("chargerId") || key.equals("stationId")
                    || key.equals("propertyId") || key.equals("tripId") || key.equals("incidentId")
                    || key.equals("planId")) result.put(key, value);
        });
        return result;
    }

    private String expectedImpact(String actionType) {
        return switch (actionType) {
            case "DISABLE_NEW_BOOKINGS", "PUT_CONNECTOR_IN_MAINTENANCE" ->
                    "Future assignments stop using the connector; healthy sibling connectors remain eligible.";
            case "CREATE_MAINTENANCE_TICKET", "REQUEST_SERVICE", "REQUEST_TATA_SERVICE" ->
                    "One traceable maintenance workflow is opened or escalated without duplicating active work.";
            case "APPLY_PRICE_RECOMMENDATION" -> "The station tariff changes only inside the saved Company limit.";
            case "CREATE_PROPERTY_DRAFT" -> "A private draft is created; it is not published.";
            case "SUBMIT_PROPERTY_FOR_VERIFICATION" -> "The property enters verification and stays undiscoverable.";
            case "PUBLISH_PROPERTY" -> "A verified property becomes discoverable in the marketplace.";
            default -> "Only the named, account-scoped resource is changed.";
        };
    }

    private boolean requiresFreshApproval(AgentWorkStatus status, String actionType) {
        return actionType != null && (status == AgentWorkStatus.NEEDS_APPROVAL || status == AgentWorkStatus.PREPARED);
    }

    private boolean terminal(AgentWorkStatus status) {
        return status == AgentWorkStatus.COMPLETED || status == AgentWorkStatus.DONE
                || status == AgentWorkStatus.FAILED || status == AgentWorkStatus.STALE
                || status == AgentWorkStatus.CANCELLED;
    }

    private String revisionKey(String base, Long previousId) {
        String suffix = ":revision-" + Objects.toString(previousId, "next");
        return limit(base, 180 - suffix.length()) + suffix;
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
            String json = objectMapper.writeValueAsString(payload);
            if (json.length() > 5000) throw new IllegalArgumentException("Prepared agent record exceeds the audit payload limit");
            return json;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to persist the prepared agent record", exception);
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

    private String nullSafeCorrelation(Long accountId, AgentWorkspace workspace, String action, Long resourceId) {
        if (resourceId != null && (action.contains("CONNECTOR") || action.contains("BOOKING")
                || action.contains("MAINTENANCE") || action.contains("SERVICE"))) {
            return "connector-" + resourceId;
        }
        return "operation-" + stableHash(accountId + ":" + workspace + ":" + action + ":" + resourceId);
    }

    private String stableHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
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
