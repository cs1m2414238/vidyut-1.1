package com.vidyut.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.AccessMode;
import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkItem;
import com.vidyut.agent.entity.AgentWorkPriority;
import com.vidyut.agent.entity.AgentWorkStatus;
import com.vidyut.agent.repository.AgentWorkItemRepository;
import com.vidyut.agent.repository.AgentActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AgentWorkQueueServiceTest {
    @Mock private AgentWorkItemRepository repository;
    @Mock private AgentActivityRepository activityRepository;
    private AgentWorkQueueService service;

    @BeforeEach
    void setUp() {
        service = new AgentWorkQueueService(repository, activityRepository, new ObjectMapper());
        lenient().when(repository.save(any(AgentWorkItem.class))).thenAnswer(invocation -> {
            AgentWorkItem item = invocation.getArgument(0);
            if (item.getId() == null) item.setId(99L);
            return item;
        });
    }

    @Test
    void queueIsStrictlyScopedToTheAuthenticatedAccountAndWorkspace() {
        AgentWorkItem approval = item(10L, AgentWorkspace.HOST, AgentWorkStatus.NEEDS_APPROVAL);
        AgentWorkItem done = item(11L, AgentWorkspace.HOST, AgentWorkStatus.DONE);
        when(repository.findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(42L, AgentWorkspace.HOST))
                .thenReturn(List.of(approval, done));

        var queue = service.queue(42L, AccessMode.HOST);

        assertThat(queue.workspace()).isEqualTo(AgentWorkspace.HOST);
        assertThat(queue.items()).extracting(item -> item.id()).containsExactly(10L, 11L);
        assertThat(queue.counts().get(AgentWorkStatus.NEEDS_APPROVAL)).isEqualTo(1);
        assertThat(queue.counts().get(AgentWorkStatus.DONE)).isEqualTo(1);
        assertThat(queue.counts().get(AgentWorkStatus.ATTENTION)).isZero();
        verify(repository).findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(42L, AgentWorkspace.HOST);
    }

    @Test
    void upsertReusesTheStableWorkKeyAndPreservesOneQueueItem() {
        AgentWorkItem existing = item(7L, AgentWorkspace.COMPANY, AgentWorkStatus.ATTENTION);
        existing.setWorkKey("company:action:create-ticket:connector:9");
        when(repository.findByAccountIdAndWorkspaceAndWorkKey(5L, AgentWorkspace.COMPANY,
                "company:action:create-ticket:connector:9")).thenReturn(Optional.of(existing));

        AgentWorkItem saved = service.upsert(5L, AgentWorkspace.COMPANY,
                "company:action:create-ticket:connector:9", "OPERATION", AgentWorkStatus.NEEDS_APPROVAL,
                AgentWorkPriority.HIGH, "Create ticket", "Backend-validated maintenance action",
                "CREATE_MAINTENANCE_TICKET", Map.of("chargerId", 9), "CONNECTOR", 9L, null);

        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo(AgentWorkStatus.NEEDS_APPROVAL);
        assertThat(saved.getActionPayloadJson()).contains("chargerId").contains("9");
        assertThat(saved.getIdempotencyKey()).startsWith("agent-").isNotEqualTo("idempotency-7");
    }

    @Test
    void completedIdempotencyKeySuppressesASecondExecution() {
        AgentWorkItem completed = item(15L, AgentWorkspace.COMPANY, AgentWorkStatus.COMPLETED);
        completed.setActionType("CREATE_MAINTENANCE_TICKET");
        completed.setResourceType("CONNECTOR");
        completed.setResourceId(9L);
        completed.setExecutionResultJson("{\"ticketId\":77}");
        completed.setResultSummary("Ticket 77 created");
        when(repository.findOwnedForUpdate(15L, 42L, AgentWorkspace.COMPANY)).thenReturn(Optional.of(completed));

        var lease = service.beginExecution(42L, AgentWorkspace.COMPANY, 15L, completed.getIdempotencyKey(),
                "CREATE_MAINTENANCE_TICKET", "CONNECTOR", 9L, true);

        assertThat(lease.duplicate()).isTrue();
        assertThat(lease.previousResult()).containsEntry("ticketId", 77);
        assertThat(completed.getStatus()).isEqualTo(AgentWorkStatus.COMPLETED);
    }

    @Test
    void aNewPlanAfterCompletionCreatesANewLifecycleRevision() {
        String workKey = "company:action:simulate-fault:connector:18";
        Map<String, Object> payload = Map.of("chargerId", 18, "expectedStatus", "ONLINE");
        AgentWorkItem first = service.upsert(5L, AgentWorkspace.COMPANY, workKey, "OPERATION",
                AgentWorkStatus.NEEDS_APPROVAL, AgentWorkPriority.HIGH, "Fault connector", "Synthetic fault",
                "SIMULATE_DEMO_FAULT", payload, "CONNECTOR", 18L, null);
        first.setStatus(AgentWorkStatus.COMPLETED);
        when(repository.findByAccountIdAndWorkspaceAndWorkKey(5L, AgentWorkspace.COMPANY, workKey))
                .thenReturn(Optional.of(first));
        when(repository.findByAccountIdAndWorkspaceAndWorkKey(5L, AgentWorkspace.COMPANY,
                workKey + ":revision-99")).thenReturn(Optional.empty());

        AgentWorkItem second = service.upsert(5L, AgentWorkspace.COMPANY, workKey, "OPERATION",
                AgentWorkStatus.NEEDS_APPROVAL, AgentWorkPriority.HIGH, "Fault connector", "Synthetic fault",
                "SIMULATE_DEMO_FAULT", payload, "CONNECTOR", 18L, null);

        assertThat(second).isNotSameAs(first);
        assertThat(second.getWorkKey()).isEqualTo(workKey + ":revision-99");
        assertThat(second.getStatus()).isEqualTo(AgentWorkStatus.NEEDS_APPROVAL);
        assertThat(second.getIdempotencyKey()).isNotEqualTo(first.getIdempotencyKey());
    }

    @Test
    void expiredApprovalBecomesStaleBeforeExecution() {
        AgentWorkItem approval = item(16L, AgentWorkspace.HOST, AgentWorkStatus.NEEDS_APPROVAL);
        approval.setActionType("REQUEST_SERVICE");
        approval.setResourceType("CONNECTOR");
        approval.setResourceId(9L);
        approval.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findOwnedForUpdate(16L, 42L, AgentWorkspace.HOST)).thenReturn(Optional.of(approval));

        var lease = service.beginExecution(42L, AgentWorkspace.HOST, 16L, approval.getIdempotencyKey(),
                "REQUEST_SERVICE", "CONNECTOR", 9L, true);

        assertThat(lease.stale()).isTrue();
        assertThat(approval.getStatus()).isEqualTo(AgentWorkStatus.STALE);
        assertThat(approval.getFailureReason()).contains("expired");
    }

    private AgentWorkItem item(Long id, AgentWorkspace workspace, AgentWorkStatus status) {
        return AgentWorkItem.builder().id(id).accountId(42L).workspace(workspace).workKey("work-" + id)
                .idempotencyKey("idempotency-" + id).correlationId("trace-" + id)
                .category("TEST").status(status).priority(AgentWorkPriority.MEDIUM)
                .title("Work " + id).detail("Scoped work item").build();
    }
}
