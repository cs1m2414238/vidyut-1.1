package com.vidyut.booking.service;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.dto.BookingStatusUpdateRequest;

import java.util.List;
import java.time.LocalDate;
import com.vidyut.booking.dto.BookingSlotResponse;

public interface BookingService {
    BookingResponse createBooking(BookingCreateRequest request, Long userId);
    BookingResponse getBookingById(Long id);
    BookingResponse getBookingById(Long id, Long userId);
    List<BookingResponse> getBookingsByUserId(Long userId);
    long getUnreadActiveCount(Long userId);
    void markBookingsSeen(Long userId);
    BookingResponse updateBookingStatus(Long id, BookingStatusUpdateRequest request);
    BookingResponse cancelBooking(Long id);
    BookingResponse cancelBooking(Long id, Long userId);
    BookingResponse cancelBookingWithoutFee(Long id, Long userId, String reason);
    List<BookingSlotResponse> getAvailability(Long stationId, LocalDate date);
}
