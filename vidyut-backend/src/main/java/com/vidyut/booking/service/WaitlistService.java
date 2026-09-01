package com.vidyut.booking.service;

import com.vidyut.booking.dto.*;
import java.util.List;

public interface WaitlistService {
    WaitlistResponse join(Long userId, WaitlistRequest request);
    List<WaitlistResponse> list(Long userId);
    WaitlistResponse cancel(Long userId, Long id);
    void promoteNext(Long stationId);
}
