package com.vidyut.vehicle.config;

import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriyanshuDemoGarageInitializerTest {

    @Mock EvUserProfileRepository profileRepository;
    @Mock VehicleRepository vehicleRepository;

    @Test
    void seedsRealIndianEvsAndClearlyLabelledConnectorEdgeCases() throws Exception {
        when(profileRepository.findAll()).thenReturn(List.of(
                EvUserProfile.builder().accountId(5L).fullName("Priyanshu Sharma").build()));
        when(vehicleRepository.findAll()).thenReturn(List.of());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new PriyanshuDemoGarageInitializer(profileRepository, vehicleRepository)
                .run(new DefaultApplicationArguments());

        ArgumentCaptor<Vehicle> vehicles = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository, org.mockito.Mockito.times(8)).save(vehicles.capture());
        assertThat(vehicles.getAllValues()).extracting(Vehicle::getMakeAndModel)
                .contains("Tata Nexon EV 45", "Tata Tigor EV", "Mahindra BE 6 79 kWh",
                        "Mahindra XEV 9e 79 kWh", "Demo CHAdeMO Test Vehicle",
                        "Demo GB/T Test Vehicle", "Demo Type 2 AC Test Vehicle",
                        "Demo Type 1 AC Test Vehicle");
        assertThat(vehicles.getAllValues().stream()
                .flatMap(vehicle -> vehicle.getSupportedConnectors().stream())
                .collect(java.util.stream.Collectors.toSet()))
                .containsAll(Set.of(ConnectorType.CCS2, ConnectorType.TYPE1, ConnectorType.TYPE2,
                        ConnectorType.CHADEMO, ConnectorType.GB_T));
    }

    @Test
    void doesNotSeedAnotherUsersProfile() throws Exception {
        when(profileRepository.findAll()).thenReturn(List.of(
                EvUserProfile.builder().accountId(9L).fullName("Another Driver").build()));

        new PriyanshuDemoGarageInitializer(profileRepository, vehicleRepository)
                .run(new DefaultApplicationArguments());

        verify(vehicleRepository, never()).save(any());
    }
}
