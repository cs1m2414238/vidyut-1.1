package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostBankRequest {
    @NotBlank @Size(max = 150) private String accountHolder;
    @NotBlank @Size(max = 120) private String bankName;
    @NotBlank @Pattern(regexp = "^[0-9]{4,18}$") private String accountNumber;
    @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code") private String ifscCode;
    @Size(max = 120) private String payoutUpi;
}
