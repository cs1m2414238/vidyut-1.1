package com.vidyut.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.agent.entity.AgentDomainEvent;
import com.vidyut.agent.entity.AgentEventType;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkStatus;
import com.vidyut.agent.repository.AgentDomainEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEventProjectionServiceTest {
    @Mock AgentDomainEventRepository eventRepository;
    @Mock AgentWorkQueueService workQueueService;
    private AgentEventProjectionService service;

    @BeforeEach
    void setUp() {
        service = new AgentEventProjectionService(eventRepository, workQueueService, new ObjectMapper());
    }

    @Test
    void oneFaultProjectsOnlyTheOwningCompanyAndHostWithTheSameTrace() {
        AgentOutboxEvent outbox = AgentOutboxEvent.builder().id(51L).eventKey("connector-faulted:81")
                .eventType(AgentEventType.CONNECTOR_FAULTED).aggregateType("CONNECTOR").aggregateId(21L)
                .correlationId("connector-21").payloadJson("""
                        {"incidentCode":"INC-AGRA","stationId":9,"stationName":"Agra Hub",
                         "connectorId":21,"chargerCode":"AGRA-CCS2-01","severity":"CRITICAL",
                         "reason":"Heartbeat lost","companyAccountId":30,"hostAccountId":40,
                         "impact":{"affectedJourneys":1}}
                        """).build();
        when(eventRepository.findByEventKey(outbox.getEventKey())).thenReturn(Optional.empty());
        when(eventRepository.saveAndFlush(any(AgentDomainEvent.class))).thenAnswer(invocation -> {
            AgentDomainEvent event = invocation.getArgument(0);
            event.setId(91L);
            return event;
        });

        service.project(outbox);

        verify(workQueueService).upsertTraced(eq(30L), eq(AgentWorkspace.COMPANY), eq("event:inc-agra:company"),
                eq("INCIDENT_TRIAGE"), eq(AgentWorkStatus.PENDING), any(), any(), any(), any(), any(),
                eq("CONNECTOR"), eq(21L), eq(91L), eq("connector-21"), any(), any(), any());
        verify(workQueueService).upsertTraced(eq(40L), eq(AgentWorkspace.HOST), eq("event:inc-agra:host"),
                eq("HOSTED_CHARGER_INCIDENT"), eq(AgentWorkStatus.PENDING), any(), any(), any(), any(), any(),
                eq("CONNECTOR"), eq(21L), eq(91L), eq("connector-21"), any(), any(), any());
        verify(workQueueService, times(2)).upsertTraced(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
