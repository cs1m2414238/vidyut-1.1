package com.vidyut.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.admin.entity.NetworkIncident;
import com.vidyut.agent.entity.AgentEventType;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Transactional publisher for agent-facing domain events. Callers invoke this
 * inside the domain mutation transaction; durable projection is handled by the
 * outbox dispatcher after commit.
 */
@Service
@RequiredArgsConstructor
public class AgentDomainEventService {
    private final AgentOutboxEventRepository outboxRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentOutboxEvent connectorFaulted(ChargingStation station, ChargingConnector connector,
            NetworkIncident incident, Long actorAccountId, Map<String, Object> impact) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("incidentCode", incident.getIncidentCode());
        payload.put("stationId", station.getId());
        payload.put("stationName", station.getName());
        payload.put("connectorId", connector.getId());
        payload.put("chargerCode", connector.getChargerCode());
        payload.put("connectorType", connector.getType().name());
        payload.put("connectorStatus", connector.getStatus().name());
        payload.put("severity", incident.getSeverity().name());
        payload.put("reason", incident.getDescription());
        payload.put("companyAccountId", companyAccountId(station));
        payload.put("hostAccountId", hostAccountId(station));
        payload.put("impact", impact == null ? Map.of() : impact);
        return enqueue("connector-faulted:" + incident.getId(), AgentEventType.CONNECTOR_FAULTED,
                "CONNECTOR", connector.getId(), actorAccountId, "connector-" + connector.getId(), payload);
    }

    @Transactional
    public AgentOutboxEvent connectorRestored(ChargingStation station, ChargingConnector connector, Long actorAccountId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stationId", station.getId());
        payload.put("stationName", station.getName());
        payload.put("connectorId", connector.getId());
        payload.put("chargerCode", Objects.toString(connector.getChargerCode(), "Connector " + connector.getId()));
        payload.put("status", connector.getStatus().name());
        payload.put("companyAccountId", companyAccountId(station));
        payload.put("hostAccountId", hostAccountId(station));
        return enqueue("connector-restored:" + connector.getId() + ":" + connector.getStatusChangedAt(),
                AgentEventType.CONNECTOR_RESTORED, "CONNECTOR", connector.getId(), actorAccountId,
                "connector-" + connector.getId(), payload);
    }

    private AgentOutboxEvent enqueue(String key, AgentEventType type, String aggregateType, Long aggregateId,
            Long actorAccountId, String correlationId, Map<String, Object> payload) {
        return outboxRepository.findByEventKey(key).orElseGet(() -> outboxRepository.save(
                AgentOutboxEvent.builder().eventKey(key).eventType(type).aggregateType(aggregateType)
                        .aggregateId(aggregateId).actorAccountId(actorAccountId).correlationId(correlationId)
                        .payloadJson(json(payload)).build()));
    }

    private Long companyAccountId(ChargingStation station) {
        Long companyId = station.getOperatorCompanyId() != null
                ? station.getOperatorCompanyId() : station.getSupplierCompanyId();
        if (companyId == null) return null;
        return companyRepository.findById(companyId)
                .map(company -> company.getAccount() == null ? null : company.getAccount().getId()).orElse(null);
    }

    private Long hostAccountId(ChargingStation station) {
        return station.getPropertyOwnerAccountId() != null ? station.getPropertyOwnerAccountId() : station.getHostUserId();
    }

    private String json(Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            if (value.length() > 5000) throw new IllegalArgumentException("Agent outbox event exceeds the audit payload limit");
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to persist the agent outbox event", exception);
        }
    }
}
