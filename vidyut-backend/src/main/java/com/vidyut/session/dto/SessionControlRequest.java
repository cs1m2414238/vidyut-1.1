package com.vidyut.session.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SessionControlRequest {
    @NotBlank
    private String action;
}
