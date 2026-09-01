package com.vidyut.host.service;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.host.dto.HostChargerStatusRequest;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HostOperatorBoundaryTest {
    @Mock HostProfileRepository hostProfileRepository;
    @Mock ChargingConnectorRepository connectorRepository;
    @InjectMocks HostOperationsService service;

    @Test void verifiedHostCannotFaultCompanyOperatedEquipmentEvenWithApproval() {
        when(hostProfileRepository.findById(7L)).thenReturn(Optional.of(HostProfile.builder().accountId(7L)
                .account(Account.builder().id(7L).enabled(true).emailVerified(true).build())
                .verified(true).verificationStatus(HostVerificationStatus.VERIFIED).build()));
        var station = ChargingStation.builder().id(1L).hostUserId(7L).operatorCompanyId(3L).ownershipType(StationOwnershipType.HOST_PARTNERED).build();
        var charger = ChargingConnector.builder().id(11L).station(station).type(ConnectorType.CCS2).build();
        when(connectorRepository.findByIdAndStation_HostUserId(11L, 7L)).thenReturn(Optional.of(charger));
        var request = new HostChargerStatusRequest();
        request.setStatus(ChargerStatus.FAULT);
        request.setImpactApproved(true);
        assertThatThrownBy(() -> service.updateChargerStatus(7L, 11L, request))
                .isInstanceOf(com.vidyut.common.exception.ForbiddenException.class).hasMessageContaining("Company");
        verify(connectorRepository, never()).save(any());
    }
}
