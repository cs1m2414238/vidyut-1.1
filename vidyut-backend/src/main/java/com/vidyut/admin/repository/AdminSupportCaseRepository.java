package com.vidyut.admin.repository;

import com.vidyut.admin.entity.AdminSupportCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminSupportCaseRepository extends JpaRepository<AdminSupportCase, Long> {
    List<AdminSupportCase> findAllByOrderByUpdatedAtDesc();
    List<AdminSupportCase> findByAccountIdOrderByUpdatedAtDesc(Long accountId);
}
