package com.vidyut.admin.service;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.admin.dto.AccountOperationalControlRequest;
import com.vidyut.admin.entity.AccountOperationalControl;
import com.vidyut.admin.entity.AdminAccount;
import com.vidyut.admin.entity.AdminRole;
import com.vidyut.admin.entity.OperationalControlType;
import com.vidyut.admin.repository.AccountOperationalControlRepository;
import com.vidyut.admin.repository.AdminAuditLogRepository;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalControlServiceTest {
    @Mock AccountOperationalControlRepository controlRepository;
    @Mock AccountRepository accountRepository;
    @Mock CompanyRepository companyRepository;
    @Mock ChargingStationRepository stationRepository;
    @Mock AdminAccessService access;
    @Mock AdminAuditLogRepository auditRepository;
    @Mock NotificationService notificationService;
    @InjectMocks OperationalControlService service;

    @Test
    void restrictsOnlyNewBookingsWithoutDisablingTheEvUserIdentity() {
        Account user = Account.builder().id(5L).email("priyanshu@example.test")
                .accountType(AccountType.INDIVIDUAL).roles(Set.of(AccountRole.ROLE_EV_USER))
                .enabled(true).build();
        when(access.require(any())).thenReturn(AdminAccount.builder().accountId(1L).adminRole(AdminRole.SUPER_ADMIN).build());
        when(accountRepository.findById(5L)).thenReturn(Optional.of(user));
        when(controlRepository.findById(5L)).thenReturn(Optional.empty());
        when(controlRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(5L, new AccountOperationalControlRequest(
                OperationalControlType.RESTRICT_NEW_BOOKINGS, true,
                "Repeated fraudulent booking attempts require review.", null));

        ArgumentCaptor<AccountOperationalControl> saved = ArgumentCaptor.forClass(AccountOperationalControl.class);
        verify(controlRepository).save(saved.capture());
        assertThat(saved.getValue().isRestrictNewBookings()).isTrue();
        assertThat(user.isEnabled()).isTrue();
        verify(notificationService).sendNotification(any(), any(), any(), any());
    }

    @Test
    void enforcedBookingRestrictionStillAllowsTheAccountToBeReadAndAuthenticated() {
        when(controlRepository.findById(5L)).thenReturn(Optional.of(
                AccountOperationalControl.builder().accountId(5L).restrictNewBookings(true).build()));

        assertThatThrownBy(() -> service.assertBookingAllowed(5L, new ChargingStation()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("bookings");
    }
}
