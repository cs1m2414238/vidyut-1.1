package com.vidyut.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyProfileUpdateRequest {
    @NotBlank
    @Size(max = 200)
    private String companyName;
    @NotBlank
    @Size(max = 150)
    private String contactName;
    @Email
    private String supportEmail;
    @Size(max = 30)
    private String supportPhone;
    @Size(max = 500)
    private String businessAddress;
    @Size(max = 255)
    private String website;
}
