package com.vidyut.agent.repository;

import com.vidyut.agent.entity.AgentDomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentDomainEventRepository extends JpaRepository<AgentDomainEvent, Long> {
    Optional<AgentDomainEvent> findByEventKey(String eventKey);
}
