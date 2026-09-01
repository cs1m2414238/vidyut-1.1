package com.vidyut.auth.dto;

import com.vidyut.account.entity.AccessMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthRequest {
    @NotBlank(message = "Google access token is required")
    private String accessToken;

    private AccessMode requestedMode;
}
