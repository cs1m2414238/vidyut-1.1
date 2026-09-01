package com.vidyut.booking.service;

import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${vidyut.notifications.reminder-scan-ms:60000}")
    @Transactional
    public void sendUpcomingBookingReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (Booking booking : bookingRepository.findPendingReminders(
                BookingStatus.CONFIRMED, now, now.plusMinutes(30))) {
            notificationService.sendNotification(booking.getUserId(), "Charging slot in 30 minutes",
                    "Your booking at " + booking.getStationName() + " starts at " + booking.getStartTime() + ".",
                    NotificationType.BOOKING_REMINDER, "vidyut://booking/" + booking.getId());
            booking.setReminderSent(true);
            bookingRepository.save(booking);
        }
    }
}
