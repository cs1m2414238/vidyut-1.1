package com.vidyut.host.dto;

import lombok.Data;

@Data
public class HostSettingsRequest {
    private boolean emailNotifications;
    private boolean pushNotifications;
    private boolean autoAvailability;
}
