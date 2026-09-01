package com.vidyut.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushDeviceRequest {
    @NotBlank
    @Size(max = 500)
    private String token;

    @NotBlank
    @Size(max = 20)
    private String platform;
}
