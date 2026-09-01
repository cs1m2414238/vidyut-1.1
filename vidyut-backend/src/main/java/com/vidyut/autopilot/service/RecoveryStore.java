package com.vidyut.autopilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.autopilot.entity.AutopilotTrip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class RecoveryStore {
    private final ObjectMapper json;
    public RecoverySession read(AutopilotTrip trip) {
        if (trip.getRecoveryJson() == null) return null;
        try { return json.readValue(trip.getRecoveryJson(), RecoverySession.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read stored recovery proposal", e); }
    }
    public void write(AutopilotTrip trip, RecoverySession session) {
        trip.setUpdatedAt(java.time.LocalDateTime.now());
        try { trip.setRecoveryJson(json.writeValueAsString(session)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot persist recovery proposal", e); }
    }
}
