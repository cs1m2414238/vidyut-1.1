package com.vidyut.company.service;

import com.vidyut.autopilot.repository.*;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.repository.*;
import com.vidyut.station.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CompanyOperatorContextServiceTest {
    @Mock AutopilotStopRepository stopRepository;
    @Mock AutopilotTripRepository tripRepository;
    @Mock LandListingRepository propertyRepository;
    @Mock InstallationRequestRepository requestRepository;
    @Mock InstallationProposalRepository proposalRepository;
    @InjectMocks CompanyOperatorContextService service;

    @Test void partneredIssueQueryExcludesCompanyOwnedStationsAndPreservesHealthyConnector() {
        var agra = station(1L, "Agra", StationOwnershipType.HOST_PARTNERED, ChargerStatus.FAULT, ConnectorType.CCS2);
        agra.getConnectors().add(ChargingConnector.builder().id(12L).station(agra).type(ConnectorType.CCS2).status(ChargerStatus.ONLINE).available(true).build());
        var gwalior = station(2L, "Gwalior", StationOwnershipType.COMPANY_OWNED, ChargerStatus.FAULT, ConnectorType.CCS2);
        var context = service.inspect(7L, "Show me all Host-partnered stations with operational issues", List.of(agra, gwalior), List.of());
        @SuppressWarnings("unchecked") var priorities = (List<Map<String, Object>>) context.get("maintenancePriorities");
        assertThat(priorities).hasSize(1);
        assertThat(priorities.get(0)).containsEntry("stationId", 1L).containsEntry("faultedConnectors", 1L)
                .containsEntry("availableCcs2", 1L).containsEntry("downtimeMinutes", null);
    }

    @Test void acCoverageUsesActualConnectorTypesAndOccupancyIsNotAFault() {
        var ac = station(1L, "Agra", StationOwnershipType.HOST_PARTNERED, ChargerStatus.CHARGING, ConnectorType.TYPE2);
        var dc = station(2L, "Gwalior", StationOwnershipType.COMPANY_OWNED, ChargerStatus.ONLINE, ConnectorType.CCS2);
        var context = service.inspect(7L, "Which stations have only AC charging?", List.of(ac, dc), List.of());
        assertThat(context).containsEntry("acOnlyCount", 1).containsEntry("coverageGapCount", 1).containsEntry("maintenancePriorityCount", 0);
    }

    @Test void realPartnershipResolvesItsPropertyThroughTheScopedInstallationRequest() {
        var station = station(1L, "Agra", StationOwnershipType.HOST_PARTNERED, ChargerStatus.ONLINE, ConnectorType.CCS2);
        station.setHostUserId(8L);
        station.setHostPartnershipId(50L);
        station.setSourceInstallationRequestId(50L);
        var property = com.vidyut.land.entity.LandListing.builder().id(90L).hostUserId(8L).title("Correct property").build();
        org.mockito.Mockito.when(requestRepository.findByIdAndCompany_Account_Id(50L, 7L)).thenReturn(Optional.of(
                com.vidyut.marketplace.entity.InstallationRequest.builder().id(50L).property(property).build()));
        var context = service.inspect(7L, "Show partnered stations", List.of(station), List.of());
        @SuppressWarnings("unchecked") var rows = (List<Map<String, Object>>) context.get("stations");
        assertThat(rows.get(0)).containsEntry("propertyId", 90L).containsEntry("propertyTitle", "Correct property");
        org.mockito.Mockito.verify(propertyRepository, org.mockito.Mockito.never()).findById(50L);
    }

    private ChargingStation station(Long id, String city, StationOwnershipType ownership, ChargerStatus status, ConnectorType type) {
        var station = ChargingStation.builder().id(id).name(city + " Demo Charging Hub").city(city).ownershipType(ownership).connectors(new ArrayList<>()).build();
        station.getConnectors().add(ChargingConnector.builder().id(id * 10).station(station).type(type).status(status)
                .available(status == ChargerStatus.ONLINE).healthScore(100).build());
        return station;
    }
}
