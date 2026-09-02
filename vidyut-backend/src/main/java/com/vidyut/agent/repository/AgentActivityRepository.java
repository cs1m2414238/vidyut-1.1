package com.vidyut.agent.repository;

import com.vidyut.agent.entity.AgentActivity;
import com.vidyut.agent.entity.AgentWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentActivityRepository extends JpaRepository<AgentActivity, Long> {
    List<AgentActivity> findTop50ByAccountIdAndWorkspaceOrderByOccurredAtDesc(Long accountId, AgentWorkspace workspace);
}
