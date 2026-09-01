package com.vidyut.agent.repository;

import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkItem;
import com.vidyut.agent.entity.AgentWorkStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentWorkItemRepository extends JpaRepository<AgentWorkItem, Long> {
    Optional<AgentWorkItem> findByAccountIdAndWorkspaceAndWorkKey(Long accountId, AgentWorkspace workspace, String workKey);

    List<AgentWorkItem> findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(Long accountId, AgentWorkspace workspace);

    List<AgentWorkItem> findByResourceTypeAndResourceIdAndStatusIn(
            String resourceType, Long resourceId, Collection<AgentWorkStatus> statuses);
}
