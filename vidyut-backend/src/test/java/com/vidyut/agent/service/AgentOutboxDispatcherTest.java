package com.vidyut.agent.service;

import com.vidyut.agent.entity.AgentEventType;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOutboxDispatcherTest {
    @Mock AgentOutboxEventRepository repository;
    @Mock AgentOutboxProcessor processor;

    @Test
    void publishesALockedOutboxRecordExactlyOnce() {
        AgentOutboxEvent event = event();
        when(repository.findDispatchable(any(), any(), any())).thenReturn(List.of(event));
        when(processor.process(1L)).thenReturn(true);
        AgentOutboxDispatcher dispatcher = new AgentOutboxDispatcher(repository, processor);

        assertThat(dispatcher.dispatchAvailable()).isEqualTo(1);

        verify(processor).process(1L);
    }

    @Test
    void recordsTerminalFailureAfterTheBoundedRetryLimit() {
        AgentOutboxEvent event = event();
        when(repository.findDispatchable(any(), any(), any())).thenReturn(List.of(event));
        IllegalStateException failure = new IllegalStateException("projection unavailable");
        doThrow(failure).when(processor).process(1L);
        AgentOutboxDispatcher dispatcher = new AgentOutboxDispatcher(repository, processor);

        assertThat(dispatcher.dispatchAvailable()).isZero();

        verify(processor).recordFailure(1L, failure);
    }

    private AgentOutboxEvent event() {
        return AgentOutboxEvent.builder().id(1L).eventKey("fault:1")
                .eventType(AgentEventType.CONNECTOR_FAULTED).aggregateType("CONNECTOR")
                .aggregateId(21L).correlationId("connector-21").payloadJson("{}").build();
    }
}
