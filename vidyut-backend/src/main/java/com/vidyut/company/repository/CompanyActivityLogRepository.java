package com.vidyut.company.repository;

import com.vidyut.company.entity.CompanyActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyActivityLogRepository extends JpaRepository<CompanyActivityLog, Long> {
    List<CompanyActivityLog> findTop100ByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
