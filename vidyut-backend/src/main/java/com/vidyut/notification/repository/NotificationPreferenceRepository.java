package com.vidyut.notification.repository;

import com.vidyut.notification.entity.NotificationPreference;
import com.vidyut.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    List<NotificationPreference> findByUserId(Long userId);
    Optional<NotificationPreference> findByUserIdAndType(Long userId, NotificationType type);
}
