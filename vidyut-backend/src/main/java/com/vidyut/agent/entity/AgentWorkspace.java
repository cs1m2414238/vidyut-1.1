package com.vidyut.agent.entity;

import com.vidyut.account.entity.AccessMode;

public enum AgentWorkspace {
    EV,
    HOST,
    COMPANY;

    public static AgentWorkspace from(AccessMode mode) {
        return switch (mode) {
            case EV_USER -> EV;
            case HOST -> HOST;
            case COMPANY -> COMPANY;
            case ADMIN -> throw new IllegalArgumentException("The operator work queue is available to EV, Host, and Company workspaces");
        };
    }
}
