package com.vidyut.admin.service;

import com.vidyut.admin.entity.AdminCapability;
import com.vidyut.admin.entity.AdminRole;
import com.vidyut.admin.repository.AdminAccountRepository;
import com.vidyut.common.util.CurrentUserUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminAccessServiceTest {
    private final AdminAccessService service = new AdminAccessService(
            mock(AdminAccountRepository.class), mock(CurrentUserUtil.class));

    @Test
    void exposesLeastPrivilegeCapabilitiesForEveryStaffRole() {
        assertThat(service.capabilities(AdminRole.SUPER_ADMIN)).containsExactlyInAnyOrder(AdminCapability.values());
        assertThat(service.capabilities(AdminRole.OPERATIONS_ADMIN))
                .isEqualTo(Set.of(AdminCapability.OVERVIEW, AdminCapability.OPERATIONS, AdminCapability.AI_NETWORK, AdminCapability.AUDIT));
        assertThat(service.capabilities(AdminRole.FINANCE_ADMIN))
                .isEqualTo(Set.of(AdminCapability.OVERVIEW, AdminCapability.FINANCE, AdminCapability.AUDIT));
        assertThat(service.capabilities(AdminRole.VERIFICATION_ADMIN))
                .isEqualTo(Set.of(AdminCapability.OVERVIEW, AdminCapability.VERIFICATIONS, AdminCapability.AUDIT));
        assertThat(service.capabilities(AdminRole.SUPPORT_ADMIN))
                .isEqualTo(Set.of(AdminCapability.OVERVIEW, AdminCapability.ACCOUNTS, AdminCapability.SUPPORT, AdminCapability.AUDIT));
    }
}
