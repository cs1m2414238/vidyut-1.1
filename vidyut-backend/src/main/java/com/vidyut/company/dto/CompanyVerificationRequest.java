package com.vidyut.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyVerificationRequest {
    @NotBlank(message = "GST number is required")
    @Pattern(regexp = "^[0-9A-Z]{15}$", message = "GST number must contain 15 uppercase letters and digits")
    private String gstNumber;
    @NotBlank(message = "KYC document URL is required")
    @Size(max = 1000)
    private String kycDocumentUrl;
    @NotBlank(message = "Business address is required")
    @Size(max = 500)
    private String businessAddress;
}
