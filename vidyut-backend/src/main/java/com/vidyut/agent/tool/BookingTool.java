package com.vidyut.agent.tool;

import org.springframework.stereotype.Component;

@Component
public class BookingTool {
    public String checkBookingSlotAvailability(Long stationId) {
        return "Slot Available";
    }
}
