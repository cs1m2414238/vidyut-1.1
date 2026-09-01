package com.vidyut.autopilot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutopilotFaultPolicyTest {

    @Test
    void onlyFullAutopilotMayExecuteAReplacementWithoutDriverApproval() {
        assertThat(AutopilotService.automaticFaultRecoveryAllowed("FULL_AUTOPILOT")).isTrue();
        assertThat(AutopilotService.automaticFaultRecoveryAllowed("ASK_BEFORE_ACTIONS")).isFalse();
        assertThat(AutopilotService.automaticFaultRecoveryAllowed("RECOMMEND_ONLY")).isFalse();
        assertThat(AutopilotService.automaticFaultRecoveryAllowed(null)).isFalse();
    }
}
