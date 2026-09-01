package com.vidyut.company.repository;

import com.vidyut.company.entity.CompanyEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyEmployeeRepository extends JpaRepository<CompanyEmployee, Long> {
    List<CompanyEmployee> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    Optional<CompanyEmployee> findByIdAndCompanyId(Long id, Long companyId);
    boolean existsByCompanyIdAndEmailIgnoreCase(Long companyId, String email);
}
