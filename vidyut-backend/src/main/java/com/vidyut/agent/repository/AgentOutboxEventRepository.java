package com.vidyut.agent.repository;

import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentOutboxEventRepository extends JpaRepository<AgentOutboxEvent, Long> {
    Optional<AgentOutboxEvent> findByEventKey(String eventKey);

    @Query("select event from AgentOutboxEvent event where event.status in :statuses "
            + "and event.availableAt <= :now order by event.id")
    List<AgentOutboxEvent> findDispatchable(@Param("statuses") Collection<AgentOutboxStatus> statuses,
            @Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from AgentOutboxEvent event where event.id = :id")
    Optional<AgentOutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
