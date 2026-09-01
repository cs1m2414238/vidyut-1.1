package com.vidyut.notification.repository;

import com.vidyut.notification.entity.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {
    Optional<PushDevice> findByToken(String token);
    List<PushDevice> findByUserIdAndEnabledTrue(Long userId);
}
