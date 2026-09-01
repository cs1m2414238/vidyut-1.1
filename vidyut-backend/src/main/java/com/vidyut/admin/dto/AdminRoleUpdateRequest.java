package com.vidyut.admin.dto;

import com.vidyut.admin.entity.AdminRole;
import jakarta.validation.constraints.NotNull;

public record AdminRoleUpdateRequest(@NotNull AdminRole role) {}
