package com.vidyut.host.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostRescheduleRequest {
    @NotNull @Future private LocalDateTime startTime;
}
