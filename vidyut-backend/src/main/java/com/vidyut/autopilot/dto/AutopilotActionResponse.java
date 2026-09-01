package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.AutopilotActionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotActionResponse {
    private int sequenceNumber;
    private AutopilotActionState state;
    private String title;
    private String detail;
    private LocalDateTime timestamp;
}
