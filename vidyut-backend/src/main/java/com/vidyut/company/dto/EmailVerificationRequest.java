package com.vidyut.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EmailVerificationRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must contain 6 digits")
    private String code;
}
