package com.vidyut.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.agent.entity.AgentDomainEvent;
import com.vidyut.agent.entity.AgentEventType;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkPriority;
import com.vidyut.agent.entity.AgentWorkStatus;
import com.vidyut.agent.repository.AgentDomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentEventProjectionService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentDomainEventRepository eventRepository;
    private final AgentWorkQueueService workQueueService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void project(AgentOutboxEvent outbox) {
        if (eventRepository.findByEventKey(outbox.getEventKey()).isPresent()) return;
        Map<String, Object> payload = payload(outbox.getPayloadJson());
        AgentDomainEvent event = eventRepository.saveAndFlush(AgentDomainEvent.builder()
                .eventKey(outbox.getEventKey()).eventType(outbox.getEventType())
                .aggregateType(outbox.getAggregateType()).aggregateId(outbox.getAggregateId())
                .actorAccountId(outbox.getActorAccountId()).correlationId(outbox.getCorrelationId())
                .outboxEventId(outbox.getId()).payloadJson(outbox.getPayloadJson()).build());
        if (outbox.getEventType() == AgentEventType.CONNECTOR_FAULTED) {
            projectConnectorFault(event, payload);
        } else if (outbox.getEventType() == AgentEventType.CONNECTOR_RESTORED) {
            workQueueService.completeResource("CONNECTOR", outbox.getAggregateId(),
                    Objects.toString(payload.get("chargerCode"), "Connector " + outbox.getAggregateId())
                            + " was restored and verified online.");
        }
    }

    private void projectConnectorFault(AgentDomainEvent event, Map<String, Object> payload) {
        Long connectorId = number(payload.get("connectorId"));
        Long companyAccountId = number(payload.get("companyAccountId"));
        Long hostAccountId = number(payload.get("hostAccountId"));
        String incidentCode = Objects.toString(payload.get("incidentCode"), "incident-" + event.getId());
        String chargerCode = Objects.toString(payload.get("chargerCode"), "Connector " + connectorId);
        String stationName = Objects.toString(payload.get("stationName"), "managed station");
        String reason = Objects.toString(payload.get("reason"), "Connector fault detected");
        IncidentSeverity severity = severity(payload.get("severity"));
        AgentWorkPriority priority = severity == IncidentSeverity.CRITICAL
                ? AgentWorkPriority.CRITICAL : AgentWorkPriority.HIGH;
        Map<String, Object> impact = nestedMap(payload.get("impact"));
        long affected = numberOrZero(impact.get("affectedJourneys"));
        String companyDetail = severity + " incident at " + stationName + ". "
                + (affected > 0 ? affected + " active journey(s) require protected recovery. "
                : "No active journey is currently dependent on it. ")
                + "Check bookings, healthy sibling connectors, and maintenance ownership before acting on "
                + chargerCode + ".";

        if (companyAccountId != null) {
            workQueueService.upsertTraced(companyAccountId, AgentWorkspace.COMPANY,
                    "event:" + incidentCode.toLowerCase() + ":company", "INCIDENT_TRIAGE",
                    AgentWorkStatus.PENDING, priority, chargerCode + " needs operational triage",
                    companyDetail, "TRIAGE_CONNECTOR_FAULT", payload, "CONNECTOR", connectorId, event.getId(),
                    event.getCorrelationId(), "incident-" + incidentCode, null,
                    new AgentWorkQueueService.WorkExplanation(
                            "A connector fault was committed for " + chargerCode + " at " + stationName + ".",
                            affected > 0 ? affected + " active journey(s) depend on the failed connector."
                                    : "The connector is unavailable for new assignments.",
                            "Vidyut persisted the incident, excluded the connector, and protected affected journeys.",
                            "Triage the connector and confirm the maintenance response.",
                            "Hardware remediation and operator dispatch remain Company-controlled.",
                            "Healthy sibling connectors remain eligible while the failed connector stays excluded."));
        }
        if (hostAccountId != null && !Objects.equals(hostAccountId, companyAccountId)) {
            workQueueService.upsertTraced(hostAccountId, AgentWorkspace.HOST,
                    "event:" + incidentCode.toLowerCase() + ":host", "HOSTED_CHARGER_INCIDENT",
                    AgentWorkStatus.PENDING, priority, "Hosted charger incident at " + stationName,
                    chargerCode + " is unavailable. The operating Company owns the hardware response; the Host can monitor and request service.",
                    "REQUEST_SERVICE", payload, "CONNECTOR", connectorId, event.getId(), event.getCorrelationId(),
                    "incident-" + incidentCode, null,
                    new AgentWorkQueueService.WorkExplanation(
                            "A charger hosted at " + stationName + " reported a fault.",
                            "Site availability or the guest experience may be affected.",
                            "Vidyut notified the operating workflow and kept unrelated Host accounts isolated.",
                            "Monitor the response or send one scoped service request.",
                            "The Host may request service but cannot mutate Company-owned hardware silently.",
                            "The operator receives the same correlated incident without exposing other Host data."));
        }
    }

    private Map<String, Object> payload(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to read the durable agent outbox payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long numberOrZero(Object value) {
        Long number = number(value);
        return number == null ? 0 : number;
    }

    private IncidentSeverity severity(Object value) {
        try {
            return IncidentSeverity.valueOf(Objects.toString(value, "HIGH"));
        } catch (IllegalArgumentException ignored) {
            return IncidentSeverity.HIGH;
        }
    }
}
