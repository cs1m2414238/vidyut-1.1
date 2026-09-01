package com.vidyut.admin.dto;

import com.vidyut.admin.entity.AdminCapability;
import com.vidyut.admin.entity.AdminRole;

import java.time.LocalDateTime;
import java.util.Set;

public record AdminProfileResponse(
        Long accountId, String email, String displayName, AdminRole role,
        Set<AdminCapability> capabilities, LocalDateTime lastLoginAt
) {}
