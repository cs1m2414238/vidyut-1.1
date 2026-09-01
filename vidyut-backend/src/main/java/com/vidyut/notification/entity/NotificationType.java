package com.vidyut.notification.entity;

public enum NotificationType {
    CHARGING_COMPLETED,
    BOOKING_CONFIRMED,
    BOOKING_REMINDER,
    WALLET_CREDIT,
    WALLET_LOW_BALANCE,
    AUTO_RECHARGE,
    SYSTEM_ALERT,
    NEW_BOOKING,
    BOOKING_CANCELLED,
    CHARGING_STARTED,
    STATION_FULL_DIVERSION,
    BT_CHARGE_COMPLETED,
    WAITLIST_AVAILABLE,
    AGENT_REPLAN,
    PAYMENT_RECEIVED,
    FAULT_ALERT,
    LOW_UPTIME_ALERT

    ;

    public boolean isSafetyCritical() {
        return this == SYSTEM_ALERT || this == STATION_FULL_DIVERSION || this == FAULT_ALERT;
    }
}
