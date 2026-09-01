package com.vidyut.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CongestionAnalysisResponse {
    private String locationArea;
    private double congestionPercentage;
    private String peakTimeWindow;
    private String recommendation;
}
