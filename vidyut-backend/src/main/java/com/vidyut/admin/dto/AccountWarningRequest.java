package com.vidyut.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountWarningRequest(@NotBlank @Size(max = 1200) String message) {
}
