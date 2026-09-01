package com.vidyut.account.entity;

public enum AccessMode {
    EV_USER,
    HOST,
    COMPANY,
    ADMIN;

    public AccountRole role() {
        return switch (this) {
            case EV_USER -> AccountRole.ROLE_EV_USER;
            case HOST -> AccountRole.ROLE_HOST;
            case COMPANY -> AccountRole.ROLE_COMPANY;
            case ADMIN -> AccountRole.ROLE_ADMIN;
        };
    }
}
