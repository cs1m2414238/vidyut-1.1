package com.vidyut.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminAnnouncementRequest(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 2000) String message,
        @NotBlank @Pattern(regexp = "EV_OWNER|HOST|COMPANY|ALL") String audience,
        @NotBlank @Pattern(regexp = "INFO|WARNING|CRITICAL") String severity,
        @Size(max = 120) String targetState,
        @Size(max = 120) String targetCity,
        Long targetAccountId
) {}
