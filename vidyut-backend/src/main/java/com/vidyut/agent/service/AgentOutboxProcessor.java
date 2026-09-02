package com.vidyut.agent.service;

import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentOutboxStatus;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentOutboxProcessor {
    private static final Set<AgentOutboxStatus> DISPATCHABLE = Set.of(
            AgentOutboxStatus.PENDING, AgentOutboxStatus.RETRYABLE_FAILURE);

    private final AgentOutboxEventRepository repository;
    private final AgentEventProjectionService projectionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(Long eventId) {
        AgentOutboxEvent event = repository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !DISPATCHABLE.contains(event.getStatus())
                || event.getAvailableAt().isAfter(LocalDateTime.now())) return false;
        event.setStatus(AgentOutboxStatus.PROCESSING);
        repository.save(event);
        projectionService.project(event);
        event.setStatus(AgentOutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        event.setLastError(null);
        repository.save(event);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, RuntimeException failure) {
        AgentOutboxEvent event = repository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.getStatus() == AgentOutboxStatus.PUBLISHED) return;
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(limit(failure.getMessage(), 1500));
        boolean terminal = attempts >= event.getMaxAttempts();
        event.setStatus(terminal ? AgentOutboxStatus.FAILED : AgentOutboxStatus.RETRYABLE_FAILURE);
        if (!terminal) event.setAvailableAt(LocalDateTime.now().plusSeconds(Math.min(300, 1L << attempts)));
        repository.save(event);
    }

    private String limit(String value, int max) {
        String safe = value == null ? "Unknown projection failure" : value;
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
