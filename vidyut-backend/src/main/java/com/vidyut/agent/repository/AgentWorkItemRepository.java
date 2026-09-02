package com.vidyut.agent.repository;

import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkItem;
import com.vidyut.agent.entity.AgentWorkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentWorkItemRepository extends JpaRepository<AgentWorkItem, Long> {
    Optional<AgentWorkItem> findByAccountIdAndWorkspaceAndWorkKey(Long accountId, AgentWorkspace workspace, String workKey);

    Optional<AgentWorkItem> findByAccountIdAndWorkspaceAndIdempotencyKey(
            Long accountId, AgentWorkspace workspace, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from AgentWorkItem item where item.id = :id and item.accountId = :accountId "
            + "and item.workspace = :workspace")
    Optional<AgentWorkItem> findOwnedForUpdate(@Param("id") Long id, @Param("accountId") Long accountId,
            @Param("workspace") AgentWorkspace workspace);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from AgentWorkItem item where item.accountId = :accountId and item.workspace = :workspace "
            + "and item.workKey = :workKey")
    Optional<AgentWorkItem> findByWorkKeyForUpdate(@Param("accountId") Long accountId,
            @Param("workspace") AgentWorkspace workspace, @Param("workKey") String workKey);

    List<AgentWorkItem> findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(Long accountId, AgentWorkspace workspace);

    List<AgentWorkItem> findByResourceTypeAndResourceIdAndStatusIn(
            String resourceType, Long resourceId, Collection<AgentWorkStatus> statuses);
}
