package com.vidyut.autopilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyIntentParseRequest {

    @NotBlank(message = "Journey request is required")
    @Size(max = 1200, message = "Journey request must be 1200 characters or fewer")
    private String text;
}
