package com.vidyut.auth.dto;

import com.vidyut.account.entity.AccessMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchModeRequest {
    @NotNull(message = "Mode is required")
    private AccessMode mode;
}
