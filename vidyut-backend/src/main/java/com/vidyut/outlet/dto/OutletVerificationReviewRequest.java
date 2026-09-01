package com.vidyut.outlet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OutletVerificationReviewRequest {
    @NotNull
    private Boolean approved;
    private Long tierId;
    private String note;
}
