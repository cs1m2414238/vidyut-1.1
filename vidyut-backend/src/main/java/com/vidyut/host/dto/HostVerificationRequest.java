package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostVerificationRequest {
    @NotBlank @Size(max = 30) private String identityType;
    @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{4}$", message = "Provide the last 4 identity characters") private String identityLast4;
    @NotBlank @Size(max = 1000) private String kycDocumentUrl;
}
