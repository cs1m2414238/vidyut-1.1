package com.vidyut.user.dto;

import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.HostVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String contactName;
    private String companyName;
    private String registrationNumber;
    private boolean profileCompleted;
    private boolean emailVerified;
    private HostVerificationStatus hostStatus;
    private AccountRole role;
    private AccountType accountType;
    private Set<AccountRole> roles;
    private Set<AccessMode> allowedModes;
    private AccessMode defaultMode;
    private boolean enabled;
    private LocalDateTime createdAt;
}
