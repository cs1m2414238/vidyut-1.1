package com.vidyut.routing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiversionRequest {
    @NotNull(message = "Alternative station is required")
    private Long alternativeStationId;
}
