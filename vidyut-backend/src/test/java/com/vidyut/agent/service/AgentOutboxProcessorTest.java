package com.vidyut.agent.service;

import com.vidyut.agent.entity.AgentEventType;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentOutboxStatus;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOutboxProcessorTest {
    @Mock AgentOutboxEventRepository repository;
    @Mock AgentEventProjectionService projectionService;

    @Test
    void claimProjectionAndPublishShareOneProcessingTransaction() {
        AgentOutboxEvent event = event();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        AgentOutboxProcessor processor = new AgentOutboxProcessor(repository, projectionService);

        assertThat(processor.process(1L)).isTrue();

        verify(projectionService).project(event);
        assertThat(event.getStatus()).isEqualTo(AgentOutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void failedProjectionMovesFromRetryableToTerminalFailureAtTheLimit() {
        AgentOutboxEvent event = event();
        event.setAttempts(4);
        event.setMaxAttempts(5);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        AgentOutboxProcessor processor = new AgentOutboxProcessor(repository, projectionService);

        processor.recordFailure(1L, new IllegalStateException("projection unavailable"));

        assertThat(event.getStatus()).isEqualTo(AgentOutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(5);
        assertThat(event.getLastError()).contains("projection unavailable");
    }

    private AgentOutboxEvent event() {
        return AgentOutboxEvent.builder().id(1L).eventKey("fault:1")
                .eventType(AgentEventType.CONNECTOR_FAULTED).aggregateType("CONNECTOR")
                .aggregateId(21L).correlationId("connector-21").payloadJson("{}").build();
    }
}
