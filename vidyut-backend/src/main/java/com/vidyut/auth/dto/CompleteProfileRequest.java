package com.vidyut.auth.dto;

import com.vidyut.account.entity.AccessMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteProfileRequest {
    @NotNull(message = "Workspace mode is required")
    private AccessMode mode;

    @NotBlank(message = "Full name is required")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Mobile phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile phone number must contain exactly 10 digits")
    private String phone;

    @Size(max = 200)
    private String companyName;

    @Size(max = 40)
    private String registrationNumber;

    @Size(max = 150)
    private String hostDisplayName;
}
