package com.vidyut.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanySettingsRequest {
    private boolean emailNotifications;
    private boolean pushNotifications;
    @NotBlank
    @Size(max = 80)
    private String timezone;
}
