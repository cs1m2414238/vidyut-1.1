package com.vidyut.admin.dto;

import com.vidyut.admin.entity.GreenSchemeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GreenSchemeRequest(
        @NotBlank @Size(max = 220) String name,
        @NotBlank @Size(max = 180) String authority,
        @NotBlank @Size(max = 50) String schemeType,
        @Size(max = 500) String states,
        @NotBlank @Size(max = 1000) String sourceUrl,
        @NotBlank @Size(max = 2000) String summary,
        @NotNull GreenSchemeStatus status,
        LocalDate validFrom,
        LocalDate validUntil
) {}
