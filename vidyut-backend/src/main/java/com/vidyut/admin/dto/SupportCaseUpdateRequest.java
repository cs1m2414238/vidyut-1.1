package com.vidyut.admin.dto;

import com.vidyut.admin.entity.SupportCaseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupportCaseUpdateRequest(
        @NotNull SupportCaseStatus status,
        @Size(max = 1500) String note
) {}
