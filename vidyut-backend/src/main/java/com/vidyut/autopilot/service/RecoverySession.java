package com.vidyut.autopilot.service;

import com.vidyut.autopilot.dto.AutopilotRecoveryResponse;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecoverySession {
    private String incidentId;
    private Long failedStopId;
    private com.vidyut.autopilot.entity.AutopilotTripStatus originalTripStatus;
    private AutopilotRecoveryResponse evidence;
    @Builder.Default private List<RecoveryPlan> plans = new ArrayList<>();
    private String selectedPlanId;
    private String agentProvider;
}
