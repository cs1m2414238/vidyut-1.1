package com.vidyut.admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record EmergencyAccountAccessRequest(
        boolean enabled,
        @NotBlank String reason,
        @AssertTrue(message = "Emergency identity action must be explicitly confirmed") boolean confirmed
) {
}
