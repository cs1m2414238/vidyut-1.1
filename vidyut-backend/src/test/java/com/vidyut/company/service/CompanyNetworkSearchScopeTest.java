package com.vidyut.company.service;

import com.vidyut.company.dto.ChargerResponse;
import com.vidyut.company.entity.Company;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.service.ChargingStationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyNetworkSearchScopeTest {
    @Mock CompanyRepository companies;
    @Mock ChargingStationRepository stations;
    @Mock ChargingStationService stationService;
    @InjectMocks CompanyOperationsService service;

    @Test void scopesSearchBeforeMatchingIncludingLegacySupplierAndHostRecords() {
        Company company = Company.builder().id(7L).active(true).build();
        ChargingStation own = site(1, 7L);
        ChargingStation otherOperator = site(2, 8L);
        otherOperator.setSupplierCompanyId(7L);
        otherOperator.setHostUserId(10L);
        when(companies.findByAccount_Id(10L)).thenReturn(Optional.of(company));
        when(stations.findByOperatorCompanyId(7L)).thenReturn(List.of(own));
        when(stations.findBySupplierCompanyId(7L)).thenReturn(List.of(own, otherOperator));
        when(stations.findByHostUserId(10L)).thenReturn(List.of(otherOperator));
        StationResponse response = StationResponse.builder().id(1L).name("Agra Hub").build();
        when(stationService.getStationById(1L)).thenReturn(response);

        assertThat(service.getStations(10L, "agar")).containsExactly(response);
        assertThat(service.getChargers(10L, "Agra")).extracting(ChargerResponse::getId).containsExactly(11L);
        assertThat(service.getChargers(10L, "OTHER-AGRA-02")).isEmpty();
        assertThat(service.getChargers(10L, "")).extracting(ChargerResponse::getId).containsExactly(11L);
        verify(stationService, never()).getStationById(2L);
        verify(stations, never()).findAll();
    }

    private static ChargingStation site(long id, Long operator) {
        ChargingStation station = ChargingStation.builder().id(id).name("Agra Hub").city("Agra")
                .operatorCompanyId(operator).build();
        ChargingConnector connector = ChargingConnector.builder().id(id * 10 + 1).station(station)
                .type(ConnectorType.CCS2).chargerCode(id == 1 ? "DEMO-AGRA-01" : "OTHER-AGRA-02").build();
        station.setConnectors(List.of(connector));
        return station;
    }
}
