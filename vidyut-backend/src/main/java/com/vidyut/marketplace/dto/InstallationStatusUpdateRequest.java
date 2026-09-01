package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.InstallationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record InstallationStatusUpdateRequest(
        @NotNull InstallationStatus status,
        @Size(max = 1000) String note,
        LocalDate scheduledDate
) {}
