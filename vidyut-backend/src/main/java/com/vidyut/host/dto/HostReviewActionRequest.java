package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostReviewActionRequest {
    @NotBlank @Size(max = 1500) private String message;
}
