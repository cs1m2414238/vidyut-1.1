package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostAiRequest {
    @NotBlank @Size(max = 500) private String question;
}
