package com.vidyut.host.controller;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.host.service.HostOperationsService;
import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.entity.PropertyType;
import com.vidyut.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HostOperationsControllerTest {

    @Mock private HostOperationsService hostService;
    @Mock private NotificationService notificationService;
    @Mock private CurrentUserUtil currentUser;
    @InjectMocks private HostOperationsController controller;

    @Test
    void agentDraftEndpointRejectsMissingHostApproval() {
        LandListingCreateRequest request = validDraft();

        assertThatThrownBy(() -> controller.createPropertyDraft(false, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Host approval is required");

        verifyNoInteractions(hostService);
    }

    @Test
    void agentDraftEndpointDelegatesOnlyAfterApproval() {
        LandListingCreateRequest request = validDraft();
        when(currentUser.getCurrentAccountId()).thenReturn(42L);
        when(hostService.createPropertyDraft(42L, request)).thenReturn(Map.of("status", "CREATED", "propertyId", 99L));

        controller.createPropertyDraft(true, request);

        verify(hostService).createPropertyDraft(42L, request);
    }

    private LandListingCreateRequest validDraft() {
        return LandListingCreateRequest.builder()
                .title("Faizabad Airport EV Hub")
                .address("Airport Road, Faizabad")
                .city("Faizabad")
                .availableParkingBays(4)
                .availableLoadKw(80.0)
                .propertyType(PropertyType.COMMERCIAL_PARKING)
                .operatingHours("06:00-23:00")
                .build();
    }
}
