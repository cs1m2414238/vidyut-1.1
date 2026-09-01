package com.vidyut.admin.repository;

import com.vidyut.admin.entity.AdminAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAnnouncementRepository extends JpaRepository<AdminAnnouncement, Long> {
    List<AdminAnnouncement> findAllByOrderByCreatedAtDesc();
}
