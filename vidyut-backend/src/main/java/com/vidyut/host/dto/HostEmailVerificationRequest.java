package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class HostEmailVerificationRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$")
    private String code;
}
