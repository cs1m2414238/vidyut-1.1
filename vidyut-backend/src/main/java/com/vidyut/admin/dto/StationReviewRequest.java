package com.vidyut.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StationReviewRequest(
        @NotBlank @Pattern(regexp = "START_REVIEW|REQUEST_INFORMATION|ESCALATE|VERIFY|PUBLISH|SUSPEND") String action,
        @Size(max = 1200) String note
) {}
