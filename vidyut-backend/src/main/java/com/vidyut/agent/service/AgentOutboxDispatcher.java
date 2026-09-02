package com.vidyut.agent.service;

import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentOutboxStatus;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentOutboxDispatcher {
    private final AgentOutboxEventRepository repository;
    private final AgentOutboxProcessor processor;

    @Scheduled(fixedDelayString = "${vidyut.agent.outbox-dispatch-ms:2000}")
    public int dispatchAvailable() {
        List<Long> eventIds = repository.findDispatchable(
                        List.of(AgentOutboxStatus.PENDING, AgentOutboxStatus.RETRYABLE_FAILURE),
                        LocalDateTime.now(), PageRequest.of(0, 20)).stream()
                .map(AgentOutboxEvent::getId).toList();
        int published = 0;
        for (Long eventId : eventIds) {
            try {
                if (processor.process(eventId)) published++;
            } catch (RuntimeException failure) {
                processor.recordFailure(eventId, failure);
                log.warn("Agent outbox projection {} failed: {}", eventId, failure.getMessage());
            }
        }
        return published;
    }
}
