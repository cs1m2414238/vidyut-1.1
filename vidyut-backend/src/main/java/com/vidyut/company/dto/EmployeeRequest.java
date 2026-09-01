package com.vidyut.company.dto;

import com.vidyut.company.entity.EmployeeRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmployeeRequest {
    @NotBlank @Size(max = 150)
    private String name;
    @NotBlank @Email
    private String email;
    @Size(max = 30)
    private String phone;
    @NotNull
    private EmployeeRole role;
    private boolean active = true;
    @Size(max = 1000)
    private String permissions;
}
