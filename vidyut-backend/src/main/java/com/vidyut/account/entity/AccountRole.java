package com.vidyut.account.entity;

public enum AccountRole {
    ROLE_EV_USER,
    ROLE_HOST,
    ROLE_COMPANY,
    ROLE_ADMIN;

    public AccessMode mode() {
        return switch (this) {
            case ROLE_EV_USER -> AccessMode.EV_USER;
            case ROLE_HOST -> AccessMode.HOST;
            case ROLE_COMPANY -> AccessMode.COMPANY;
            case ROLE_ADMIN -> AccessMode.ADMIN;
        };
    }
}
