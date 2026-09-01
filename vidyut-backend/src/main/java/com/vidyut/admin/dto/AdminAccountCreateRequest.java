package com.vidyut.admin.dto;

import com.vidyut.admin.entity.AdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAccountCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 100) String password,
        @NotBlank @Size(max = 150) String displayName,
        @NotNull AdminRole role
) {}
