package com.vidyut.agent.service;

import com.vidyut.agent.dto.CongestionAnalysisResponse;
import org.springframework.stereotype.Service;

@Service
public class CongestionAgentService {

    public CongestionAnalysisResponse analyzeCongestion(String area) {
        return CongestionAnalysisResponse.builder()
                .locationArea(area != null ? area : "Gomti Nagar, Lucknow")
                .congestionPercentage(78.5)
                .peakTimeWindow("5:00 PM - 8:00 PM")
                .recommendation("High demand expected. Route vehicles to Vibhuti Khand or charge between 10 PM - 6 AM.")
                .build();
    }
}
