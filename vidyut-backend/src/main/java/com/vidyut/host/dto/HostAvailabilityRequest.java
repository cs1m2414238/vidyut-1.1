package com.vidyut.host.dto;

import com.vidyut.station.entity.StationAvailability;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostAvailabilityRequest {
    @NotNull private StationAvailability availability;
    @Size(max = 1500) private String weeklySchedule;
    @Size(max = 1500) private String holidaySchedule;
    @Size(max = 1000) private String chargingInstructions;
    @Min(15) @Max(480) private int bookingSlotMinutes = 60;
    private boolean autoAvailability;
    private boolean emergencyDisabled;
}
