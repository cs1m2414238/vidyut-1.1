package com.vidyut.company.service;

import com.vidyut.station.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyNetworkSearchTest {
    private final ChargingStation agra = station(1, "Agra", "Fatehabad Road, Agra, Uttar Pradesh");
    private final ChargingStation gwalior = station(2, "Gwalior", "NH-44, Madhya Pradesh");
    private final ChargingStation etawah = station(3, "Etawah", "Agra-Lucknow Expressway, Uttar Pradesh");
    private final List<ChargingStation> network = List.of(gwalior, etawah, agra);

    @ParameterizedTest
    @ValueSource(strings = {"Agra", "agra", "agar", "Agra charger", "agr charger", "  CHARGERS, Agra!!  "})
    void citySearchOnlyReturnsItsStationAndEveryConnector(String query) {
        assertThat(CompanyNetworkSearch.stations(network, query)).extracting(ChargingStation::getId).containsExactly(1L);
        assertThat(CompanyNetworkSearch.chargers(network, query)).hasSize(3)
                .allMatch(connector -> connector.getStation() == agra);
    }

    @ParameterizedTest
    @ValueSource(strings = {"agra ccs2", "CCS2 agar", "ccs2, Agra"})
    void connectorTermsNarrowWithinTheCityRegardlessOfOrder(String query) {
        assertThat(CompanyNetworkSearch.chargers(network, query))
                .extracting(ChargingConnector::getChargerCode)
                .containsExactly("DEMO-AGRA-CCS2-01", "DEMO-AGRA-CCS2-02");
    }

    @Test void exactCodePrefixAndTypeAreDistinctSearches() {
        assertThat(CompanyNetworkSearch.chargers(network, "DEMO-AGRA")).hasSize(3);
        assertThat(CompanyNetworkSearch.chargers(network, "DEMO-AGRA-CCS2-01"))
                .extracting(ChargingConnector::getChargerCode).containsExactly("DEMO-AGRA-CCS2-01");
        assertThat(CompanyNetworkSearch.chargers(network, "Agra Type 2"))
                .extracting(ChargingConnector::getChargerCode).containsExactly("DEMO-AGRA-TYPE2-01");
    }

    @Test void cityAndTypeIncludesEveryStationEvenWithDifferentCodePrefixes() {
        ChargingStation districtAgra = station(7, "Agra", "Agra district, Uttar Pradesh");
        for (ChargingConnector connector : districtAgra.getConnectors()) {
            connector.setChargerCode(connector.getChargerCode().replace("DEMO-AGRA", "DISTRICT-AGRA"));
        }
        List<ChargingStation> multipleSites = List.of(agra, districtAgra, etawah);
        for (String query : List.of("agra ccs2", "CCS2, agar", "agra-ccs2")) {
            assertThat(CompanyNetworkSearch.chargers(multipleSites, query))
                    .extracting(ChargingConnector::getId).containsExactly(11L, 12L, 71L, 72L);
        }
        assertThat(CompanyNetworkSearch.chargers(multipleSites, "DEMO-AGRA"))
                .extracting(ChargingConnector::getId).containsExactly(11L, 12L, 13L);
    }

    @Test void supportsStoredAddressStateAndStationIdentifier() {
        assertThat(CompanyNetworkSearch.stations(network, "Uttar Pradesh")).extracting(ChargingStation::getId).containsExactly(3L, 1L);
        assertThat(CompanyNetworkSearch.stations(network, "Lucknow Expressway")).extracting(ChargingStation::getId).containsExactly(3L);
        assertThat(CompanyNetworkSearch.chargers(network, "Fatehabad Road")).hasSize(3);
        assertThat(CompanyNetworkSearch.stations(network, "AGRA_DEMO_01")).extracting(ChargingStation::getId).containsExactly(1L);
    }

    @Test void typoToleranceIsNotCitySpecific() {
        ChargingStation mathura = station(4, "Mathura", "Vrindavan Road");
        assertThat(CompanyNetworkSearch.stations(List.of(agra, mathura), "Mathrua")).extracting(ChargingStation::getId).containsExactly(4L);
        ChargingStation cafe = station(5, "Café", "Market Road");
        assertThat(CompanyNetworkSearch.stations(List.of(cafe), "cafe")).extracting(ChargingStation::getId).containsExactly(5L);
        ChargingStation agartala = station(6, "Agartala", "Market Road");
        assertThat(CompanyNetworkSearch.stations(List.of(agra, agartala), "agar")).extracting(ChargingStation::getId).containsExactly(1L);
    }

    @Test void emptySearchReturnsAllAndUnknownSearchReturnsNone() {
        assertThat(CompanyNetworkSearch.stations(network, "  ")).hasSize(3);
        assertThat(CompanyNetworkSearch.chargers(network, null)).hasSize(9);
        assertThat(CompanyNetworkSearch.chargers(network, "nowhere-xyz")).isEmpty();
    }

    @Test void findsRecordsBeyondTheFirstRenderedPage() {
        List<ChargingStation> largeNetwork = new ArrayList<>();
        for (int i = 10; i < 210; i++) largeNetwork.add(station(i, "Gwalior", "NH-44"));
        largeNetwork.add(agra);
        assertThat(CompanyNetworkSearch.stations(largeNetwork, "Agra")).extracting(ChargingStation::getId).containsExactly(1L);
        assertThat(CompanyNetworkSearch.chargers(largeNetwork, "Agra")).hasSize(3);
    }

    private static ChargingStation station(long id, String city, String address) {
        ChargingStation station = ChargingStation.builder().id(id).city(city).name(city + " Demo Charging Hub")
                .address(address).operatorCompanyId(7L).demoSeedKey(city.toUpperCase() + "_DEMO_01")
                .connectors(new ArrayList<>()).build();
        for (int i = 1; i <= 3; i++) {
            ConnectorType type = i == 3 ? ConnectorType.TYPE2 : ConnectorType.CCS2;
            station.getConnectors().add(ChargingConnector.builder().id(id * 10 + i).station(station)
                    .type(type).chargerCode("DEMO-" + city.toUpperCase() + "-" + type + "-0" + (i == 3 ? 1 : i)).build());
        }
        return station;
    }
}
