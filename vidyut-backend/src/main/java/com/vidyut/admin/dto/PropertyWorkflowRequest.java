package com.vidyut.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PropertyWorkflowRequest(
        @NotBlank @Pattern(regexp = "START_REVIEW|REQUEST_INFORMATION|REQUEST_VIDEO|SCHEDULE_INSPECTION|ESCALATE|APPROVE|REJECT") String action,
        @Size(max = 1200) String note,
        LocalDateTime scheduledAt
) {}
