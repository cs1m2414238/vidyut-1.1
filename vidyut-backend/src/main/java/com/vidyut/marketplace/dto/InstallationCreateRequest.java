package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.BusinessModel;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record InstallationCreateRequest(
        @NotNull Long propertyId,
        @NotNull Long companyId,
        @NotNull Long productId,
        @Min(1) @Max(20) int quantity,
        @NotNull BusinessModel businessModel,
        @DecimalMin("0") Double budget,
        LocalDate targetInstallationDate,
        @Size(max = 1500) String message
) {}
