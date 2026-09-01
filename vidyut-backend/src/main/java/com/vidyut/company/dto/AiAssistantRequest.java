package com.vidyut.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiAssistantRequest {
    @NotBlank
    @Size(max = 500)
    private String question;
}
