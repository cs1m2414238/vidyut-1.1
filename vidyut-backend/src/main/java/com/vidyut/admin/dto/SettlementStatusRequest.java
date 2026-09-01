package com.vidyut.admin.dto;

import com.vidyut.admin.entity.SettlementStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SettlementStatusRequest(
        @NotNull SettlementStatus status,
        @Size(max = 1500) String note
) {}
