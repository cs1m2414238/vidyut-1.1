package com.vidyut.outlet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InstitutionVerificationRequest {
    @NotNull
    private Long outletId;
    @NotBlank
    @Size(max = 1000)
    private String documentUri;
}
