package com.vidyut.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.Account;
import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.entity.NetworkIncident;
import com.vidyut.agent.entity.AgentEventType;
import com.vidyut.agent.entity.AgentOutboxEvent;
import com.vidyut.agent.entity.AgentOutboxStatus;
import com.vidyut.agent.repository.AgentOutboxEventRepository;
import com.vidyut.company.entity.Company;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDomainEventServiceTest {
    @Mock AgentOutboxEventRepository outboxRepository;
    @Mock CompanyRepository companyRepository;
    private AgentDomainEventService service;

    @BeforeEach
    void setUp() {
        service = new AgentDomainEventService(outboxRepository, companyRepository, new ObjectMapper());
    }

    @Test
    void connectorFaultIsWrittenToTheTransactionalOutboxWithSharedScopeAndCorrelation() {
        Company company = Company.builder().id(3L).account(Account.builder().id(30L).build()).build();
        when(companyRepository.findById(3L)).thenReturn(Optional.of(company));
        when(outboxRepository.findByEventKey("connector-faulted:81")).thenReturn(Optional.empty());
        when(outboxRepository.save(any(AgentOutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.connectorFaulted(station(), connector(), incident(), 30L, Map.of("affectedJourneys", 1));

        ArgumentCaptor<AgentOutboxEvent> event = ArgumentCaptor.forClass(AgentOutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo(AgentOutboxStatus.PENDING);
        assertThat(event.getValue().getEventType()).isEqualTo(AgentEventType.CONNECTOR_FAULTED);
        assertThat(event.getValue().getCorrelationId()).isEqualTo("connector-21");
        assertThat(event.getValue().getPayloadJson()).contains("\"companyAccountId\":30")
                .contains("\"hostAccountId\":40").contains("\"affectedJourneys\":1");
    }

    @Test
    void repeatedFaultForTheSameIncidentReusesOneOutboxRecord() {
        AgentOutboxEvent existing = AgentOutboxEvent.builder().id(7L).eventKey("connector-faulted:81")
                .eventType(AgentEventType.CONNECTOR_FAULTED).aggregateType("CONNECTOR").aggregateId(21L)
                .correlationId("connector-21").payloadJson("{}").build();
        when(companyRepository.findById(3L)).thenReturn(Optional.of(
                Company.builder().id(3L).account(Account.builder().id(30L).build()).build()));
        when(outboxRepository.findByEventKey("connector-faulted:81")).thenReturn(Optional.of(existing));

        AgentOutboxEvent result = service.connectorFaulted(station(), connector(), incident(), null, Map.of());

        assertThat(result).isSameAs(existing);
        verify(outboxRepository, never()).save(any());
    }

    private ChargingStation station() {
        return ChargingStation.builder().id(9L).name("Agra Hub").operatorCompanyId(3L)
                .propertyOwnerAccountId(40L).hostUserId(40L).build();
    }

    private ChargingConnector connector() {
        return ChargingConnector.builder().id(21L).station(station()).chargerCode("AGRA-CCS2-01")
                .type(ConnectorType.CCS2).status(ChargerStatus.FAULT).build();
    }

    private NetworkIncident incident() {
        return NetworkIncident.builder().id(81L).incidentCode("INC-AGRA")
                .severity(IncidentSeverity.CRITICAL).description("Heartbeat lost").build();
    }
}
