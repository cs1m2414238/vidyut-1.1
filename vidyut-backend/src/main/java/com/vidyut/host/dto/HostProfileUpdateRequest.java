package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostProfileUpdateRequest {
    @NotBlank @Size(max = 150) private String displayName;
    @Size(max = 20)
    @Pattern(regexp = "^$|^(?:\\+?91)?[0-9]{10}$", message = "Enter a valid 10-digit Indian mobile number")
    private String phone;
    @Size(max = 500) private String address;
    @Size(max = 500) private String bio;
}
