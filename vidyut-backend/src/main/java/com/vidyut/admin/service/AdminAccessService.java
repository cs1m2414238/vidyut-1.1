package com.vidyut.admin.service;

import com.vidyut.admin.dto.AdminProfileResponse;
import com.vidyut.admin.entity.*;
import com.vidyut.admin.repository.AdminAccountRepository;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAccessService {
    private final AdminAccountRepository adminRepository;
    private final CurrentUserUtil currentUser;

    public AdminAccount currentAdmin() {
        return adminRepository.findById(currentUser.getCurrentAccountId())
                .filter(AdminAccount::isActive)
                .filter(admin -> admin.getAccount().isEnabled())
                .orElseThrow(() -> new ForbiddenException("This administrator account is inactive"));
    }

    public AdminAccount require(AdminCapability capability) {
        AdminAccount admin = currentAdmin();
        if (!capabilities(admin.getAdminRole()).contains(capability)) {
            throw new ForbiddenException("Your admin role cannot access this module");
        }
        return admin;
    }

    public AdminProfileResponse profile(AdminAccount admin) {
        return new AdminProfileResponse(admin.getAccountId(), admin.getAccount().getEmail(), admin.getDisplayName(),
                admin.getAdminRole(), capabilities(admin.getAdminRole()), admin.getLastLoginAt());
    }

    public Set<AdminCapability> capabilities(AdminRole role) {
        return switch (role) {
            case SUPER_ADMIN -> EnumSet.allOf(AdminCapability.class);
            case VERIFICATION_ADMIN -> EnumSet.of(AdminCapability.OVERVIEW, AdminCapability.VERIFICATIONS, AdminCapability.AUDIT);
            case SUPPORT_ADMIN -> EnumSet.of(AdminCapability.OVERVIEW, AdminCapability.ACCOUNTS,
                    AdminCapability.SUPPORT, AdminCapability.AUDIT);
            case FINANCE_ADMIN -> EnumSet.of(AdminCapability.OVERVIEW, AdminCapability.FINANCE, AdminCapability.AUDIT);
            case OPERATIONS_ADMIN -> EnumSet.of(AdminCapability.OVERVIEW, AdminCapability.OPERATIONS,
                    AdminCapability.AI_NETWORK, AdminCapability.AUDIT);
        };
    }
}
