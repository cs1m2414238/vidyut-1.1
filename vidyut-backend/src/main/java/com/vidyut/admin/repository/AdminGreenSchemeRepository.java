package com.vidyut.admin.repository;

import com.vidyut.admin.entity.AdminGreenScheme;
import com.vidyut.admin.entity.GreenSchemeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminGreenSchemeRepository extends JpaRepository<AdminGreenScheme, Long> {
    List<AdminGreenScheme> findAllByOrderByUpdatedAtDesc();
    List<AdminGreenScheme> findByStatusOrderByUpdatedAtDesc(GreenSchemeStatus status);
}
