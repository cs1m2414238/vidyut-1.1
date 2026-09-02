package com.vidyut.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.Account;
import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.entity.NetworkIncident;
import com.vidyut.agent.entity.AgentActivity;
import com.vidyut.agent.entity.AgentDomainEvent;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentOutboxStatus;
import com.vidyut.agent.entity.AgentWorkspace;
import com.vidyut.agent.entity.AgentWorkItem;
import com.vidyut.agent.entity.AgentWorkStatus;
import com.vidyut.agent.repository.AgentActivityRepository;
import com.vidyut.agent.repository.AgentDomainEventRepository;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import com.vidyut.agent.repository.AgentWorkItemRepository;
import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.company.dto.CompanyAgentActionType;
import com.vidyut.company.dto.CompanyAgentResponse;
import com.vidyut.company.entity.Company;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOperatorWorkflowIntegrationTest {
    @Mock AgentWorkItemRepository workRepository;
    @Mock AgentActivityRepository activityRepository;
    @Mock AgentOutboxEventRepository outboxRepository;
    @Mock AgentDomainEventRepository eventRepository;
    @Mock CompanyRepository companyRepository;

    private final Map<Long, AgentWorkItem> workItems = new LinkedHashMap<>();
    private final Map<String, AgentOutboxEvent> outbox = new LinkedHashMap<>();
    private final Map<String, AgentDomainEvent> events = new LinkedHashMap<>();
    private final List<AgentActivity> activities = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(1);
    private AgentWorkQueueService queueService;
    private AgentDomainEventService publisher;
    private AgentOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        queueService = new AgentWorkQueueService(workRepository, activityRepository, json);
        AgentEventProjectionService projection = new AgentEventProjectionService(eventRepository, queueService, json);
        publisher = new AgentDomainEventService(outboxRepository, companyRepository, json);
        AgentOutboxProcessor processor = new AgentOutboxProcessor(outboxRepository, projection);
        dispatcher = new AgentOutboxDispatcher(outboxRepository, processor);

        when(workRepository.save(any(AgentWorkItem.class))).thenAnswer(invocation -> {
            AgentWorkItem item = invocation.getArgument(0);
            if (item.getId() == null) item.setId(ids.getAndIncrement());
            item.setUpdatedAt(LocalDateTime.now());
            workItems.put(item.getId(), item);
            return item;
        });
        when(workRepository.findByAccountIdAndWorkspaceAndWorkKey(any(), any(), any())).thenAnswer(invocation ->
                workItems.values().stream().filter(item -> item.getAccountId().equals(invocation.getArgument(0))
                        && item.getWorkspace() == invocation.getArgument(1)
                        && item.getWorkKey().equals(invocation.getArgument(2))).findFirst());
        when(workRepository.findOwnedForUpdate(any(), any(), any())).thenAnswer(invocation -> Optional.ofNullable(
                workItems.get(invocation.<Long>getArgument(0))).filter(item ->
                        item.getAccountId().equals(invocation.getArgument(1))
                                && item.getWorkspace() == invocation.getArgument(2)));
        when(workRepository.findTop50ByAccountIdAndWorkspaceOrderByUpdatedAtDesc(any(), any())).thenAnswer(invocation ->
                workItems.values().stream().filter(item -> item.getAccountId().equals(invocation.getArgument(0))
                        && item.getWorkspace() == invocation.getArgument(1))
                        .sorted(Comparator.comparing(AgentWorkItem::getUpdatedAt).reversed()).limit(50).toList());
        when(activityRepository.save(any(AgentActivity.class))).thenAnswer(invocation -> {
            AgentActivity activity = invocation.getArgument(0);
            activity.setId(ids.getAndIncrement());
            activities.add(activity);
            return activity;
        });

        when(outboxRepository.findByEventKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(outbox.get(invocation.getArgument(0))));
        when(outboxRepository.save(any(AgentOutboxEvent.class))).thenAnswer(invocation -> {
            AgentOutboxEvent event = invocation.getArgument(0);
            if (event.getId() == null) event.setId(ids.getAndIncrement());
            outbox.put(event.getEventKey(), event);
            return event;
        });
        when(outboxRepository.findDispatchable(any(), any(), any())).thenAnswer(invocation -> outbox.values().stream()
                .filter(event -> event.getStatus() == AgentOutboxStatus.PENDING
                        || event.getStatus() == AgentOutboxStatus.RETRYABLE_FAILURE).toList());
        when(outboxRepository.findByIdForUpdate(any())).thenAnswer(invocation -> outbox.values().stream()
                .filter(event -> event.getId().equals(invocation.getArgument(0))).findFirst());
        when(eventRepository.findByEventKey(any())).thenAnswer(invocation ->
                Optional.ofNullable(events.get(invocation.getArgument(0))));
        when(eventRepository.saveAndFlush(any(AgentDomainEvent.class))).thenAnswer(invocation -> {
            AgentDomainEvent event = invocation.getArgument(0);
            event.setId(ids.getAndIncrement());
            events.put(event.getEventKey(), event);
            return event;
        });
    }

    @Test
    void faultFlowsAcrossTheThreeScopedAgentsAndAnApprovedActionExecutesOnce() {
        when(companyRepository.findById(3L)).thenReturn(Optional.of(
                Company.builder().id(3L).account(Account.builder().id(30L).build()).build()));
        ChargingStation station = ChargingStation.builder().id(9L).name("Agra Hub").operatorCompanyId(3L)
                .propertyOwnerAccountId(40L).hostUserId(40L).build();
        ChargingConnector failed = ChargingConnector.builder().id(21L).station(station).chargerCode("AGRA-CCS2-01")
                .type(ConnectorType.CCS2).status(ChargerStatus.FAULT).available(false).build();
        ChargingConnector healthySibling = ChargingConnector.builder().id(22L).station(station).chargerCode("AGRA-CCS2-02")
                .type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build();
        NetworkIncident incident = NetworkIncident.builder().id(81L).incidentCode("INC-AGRA")
                .severity(IncidentSeverity.CRITICAL).description("Heartbeat lost").build();

        publisher.connectorFaulted(station, failed, incident, 30L,
                Map.of("affectedJourneys", 1, "backupConnectorAvailable", true));
        publisher.connectorFaulted(station, failed, incident, 30L,
                Map.of("affectedJourneys", 1, "backupConnectorAvailable", true));
        assertThat(outbox).hasSize(1);
        assertThat(dispatcher.dispatchAvailable()).isEqualTo(1);

        AutopilotTrip trip = AutopilotTrip.builder().id(70L).userId(50L).destination("Bhopal").build();
        queueService.trackRecovery(trip, "ev-incident-1", AgentWorkStatus.NEEDS_APPROVAL,
                "Two complete safe alternatives were evaluated; approve the selected recovery bundle.",
                Map.of("tripId", 70L, "incidentId", "ev-incident-1", "failedConnectorId", 21L,
                        "planId", "safe-plan-1", "safeAlternatives", 2));

        var companyQueue = queueService.queue(30L, AccessMode.COMPANY);
        var hostQueue = queueService.queue(40L, AccessMode.HOST);
        var evQueue = queueService.queue(50L, AccessMode.EV_USER);
        var unrelatedHostQueue = queueService.queue(41L, AccessMode.HOST);
        assertThat(companyQueue.items()).extracting(item -> item.correlationId()).contains("connector-21");
        assertThat(hostQueue.items()).extracting(item -> item.correlationId()).contains("connector-21");
        assertThat(evQueue.items()).extracting(item -> item.correlationId()).contains("connector-21");
        assertThat(unrelatedHostQueue.items()).isEmpty();

        AgentWorkItem action = queueService.captureCompanyPlan(30L, List.of(
                new CompanyAgentResponse.RecommendedAction(CompanyAgentActionType.CREATE_MAINTENANCE_TICKET,
                        "Create maintenance ticket", "LOW", true, 21L, null, null,
                        "Open one work order for the failed connector", ChargerStatus.FAULT))).get(0);
        var first = queueService.beginExecution(30L, AgentWorkspace.COMPANY, action.getId(),
                action.getIdempotencyKey(), "CREATE_MAINTENANCE_TICKET", "CONNECTOR", 21L, true);
        queueService.completeExecution(first.item(), "Maintenance ticket 900 opened",
                Map.of("ticketId", 900, "connectorId", 21));
        var repeated = queueService.beginExecution(30L, AgentWorkspace.COMPANY, action.getId(),
                action.getIdempotencyKey(), "CREATE_MAINTENANCE_TICKET", "CONNECTOR", 21L, true);

        assertThat(repeated.duplicate()).isTrue();
        assertThat(repeated.previousResult()).containsEntry("ticketId", 900);
        assertThat(workItems.values()).filteredOn(item -> "CREATE_MAINTENANCE_TICKET".equals(item.getActionType()))
                .hasSize(1).allMatch(item -> item.getStatus() == AgentWorkStatus.COMPLETED);
        assertThat(healthySibling.getStatus()).isEqualTo(ChargerStatus.ONLINE);
        assertThat(healthySibling.isAvailable()).isTrue();
        assertThat(activities).extracting(AgentActivity::getActivityType)
                .contains("WORK_PREPARED", "ACTION_APPROVED", "EXECUTION_VERIFIED", "DUPLICATE_SUPPRESSED");
    }
}
