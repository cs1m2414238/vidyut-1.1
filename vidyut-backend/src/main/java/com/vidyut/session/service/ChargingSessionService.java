package com.vidyut.session.service;

import com.vidyut.session.dto.ChargingSessionResponse;

import java.util.List;

public interface ChargingSessionService {
    ChargingSessionResponse start(Long userId, Long bookingId);
    ChargingSessionResponse get(Long userId, Long sessionId);
    List<ChargingSessionResponse> getActive(Long userId);
    ChargingSessionResponse stop(Long userId, Long sessionId);
    ChargingSessionResponse pay(Long userId, Long sessionId);
    ChargingSessionResponse updateSoc(Long userId, Long sessionId, int batteryPercent, boolean simulated);
    ChargingSessionResponse control(Long userId, Long sessionId, String action);
}
