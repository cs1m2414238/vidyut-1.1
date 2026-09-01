package com.vidyut.admin.dto;

import com.vidyut.admin.entity.SupportCasePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupportCaseCreateRequest(
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 180) String subject,
        @NotBlank @Size(max = 2000) String description,
        @NotNull SupportCasePriority priority
) {}
