package com.vidyut.booking.controller;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.dto.BookingUnreadCountResponse;
import com.vidyut.booking.service.BookingService;
import com.vidyut.booking.service.WaitlistService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;
import com.vidyut.booking.dto.BookingSlotResponse;
import com.vidyut.booking.dto.WaitlistRequest;
import com.vidyut.booking.dto.WaitlistResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/ev/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;
    private final CurrentUserUtil currentUser;
    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(@Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking created successfully",
                bookingService.createBooking(request, currentUser.getCurrentAccountId())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings() {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getBookingsByUserId(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getMyBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getBookingById(id, currentUser.getCurrentAccountId())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<BookingUnreadCountResponse>> getUnreadBookingCount() {
        return ResponseEntity.ok(ApiResponse.success(new BookingUnreadCountResponse(
                bookingService.getUnreadActiveCount(currentUser.getCurrentAccountId()))));
    }

    @PatchMapping("/mark-seen")
    public ResponseEntity<ApiResponse<Void>> markBookingsSeen() {
        bookingService.markBookingsSeen(currentUser.getCurrentAccountId());
        return ResponseEntity.ok(ApiResponse.success("Bookings marked as seen", null));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelMyBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled successfully",
                bookingService.cancelBooking(id, currentUser.getCurrentAccountId())));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelMyBookingPatch(@PathVariable Long id) {
        return cancelMyBooking(id);
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<BookingSlotResponse>>> getAvailability(
            @RequestParam Long stationId,
            @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getAvailability(stationId,
                date != null ? date : LocalDate.now())));
    }

    @PostMapping("/waitlist")
    public ResponseEntity<ApiResponse<WaitlistResponse>> joinWaitlist(@Valid @RequestBody WaitlistRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Added to station waitlist",
                waitlistService.join(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping("/waitlist")
    public ResponseEntity<ApiResponse<List<WaitlistResponse>>> getWaitlist() {
        return ResponseEntity.ok(ApiResponse.success(waitlistService.list(currentUser.getCurrentAccountId())));
    }

    @DeleteMapping("/waitlist/{id}")
    public ResponseEntity<ApiResponse<WaitlistResponse>> cancelWaitlist(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Waitlist entry cancelled",
                waitlistService.cancel(currentUser.getCurrentAccountId(), id)));
    }
}
