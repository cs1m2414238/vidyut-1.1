package com.vidyut.station.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargingStationServiceImplTest {

    @Mock
    private ChargingStationRepository stationRepository;

    @InjectMocks
    private ChargingStationServiceImpl service;

    @Test
    void companyControlledSiteDoesNotCreateAFakeHostRelationship() {
        when(stationRepository.save(any(ChargingStation.class))).thenAnswer(invocation -> {
            ChargingStation station = invocation.getArgument(0);
            station.setId(91L);
            return station;
        });
        StationCreateRequest request = StationCreateRequest.builder()
                .name("Tata Kanpur Company Hub")
                .address("NH 19, Kanpur")
                .city("Kanpur")
                .latitude(26.4499)
                .longitude(80.3319)
                .pricePerKwh(16.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(120)
                .siteOwnershipDocumentUrl("https://evidence.example/site-control.pdf")
                .electricityConnectionDocumentUrl("https://evidence.example/electricity.pdf")
                .build();

        var response = service.createCompanyStation(request, 44L, 12L, "TATA Power");

        ArgumentCaptor<ChargingStation> saved = ArgumentCaptor.forClass(ChargingStation.class);
        org.mockito.Mockito.verify(stationRepository).save(saved.capture());
        assertThat(saved.getValue().getHostUserId()).isNull();
        assertThat(saved.getValue().getPropertyOwnerAccountId()).isEqualTo(44L);
        assertThat(saved.getValue().getOperatorCompanyId()).isEqualTo(12L);
        assertThat(saved.getValue().getOwnershipType()).isEqualTo(StationOwnershipType.COMPANY_OWNED);
        assertThat(saved.getValue().getHostPartnershipId()).isNull();
        assertThat(response.isSiteEvidenceComplete()).isTrue();
    }

    @Test
    void viewportQueryCapsResultsAtFiveHundredAndUsesDatabasePagination() {
        ReflectionTestUtils.setField(service, "demoDataEnabled", true);
        when(stationRepository.findPublishedStationsWithinBounds(
                org.mockito.ArgumentMatchers.eq(8.0), org.mockito.ArgumentMatchers.eq(37.0),
                org.mockito.ArgumentMatchers.eq(68.0), org.mockito.ArgumentMatchers.eq(98.0),
                org.mockito.ArgumentMatchers.eq(true), any(Pageable.class)))
                .thenReturn(List.of());

        service.getStationsWithinBounds(8, 37, 68, 98, 5_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(stationRepository).findPublishedStationsWithinBounds(
                org.mockito.ArgumentMatchers.eq(8.0), org.mockito.ArgumentMatchers.eq(37.0),
                org.mockito.ArgumentMatchers.eq(68.0), org.mockito.ArgumentMatchers.eq(98.0),
                org.mockito.ArgumentMatchers.eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void viewportQueryRejectsInvertedBounds() {
        assertThatThrownBy(() -> service.getStationsWithinBounds(30, 20, 70, 80, 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid latitude/longitude");
    }
}
