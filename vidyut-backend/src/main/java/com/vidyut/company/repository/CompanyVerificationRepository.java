package com.vidyut.company.repository;

import com.vidyut.company.entity.CompanyVerification;
import com.vidyut.company.entity.CompanyVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyVerificationRepository extends JpaRepository<CompanyVerification, Long> {
    Optional<CompanyVerification> findByCompany_Id(Long companyId);
    Optional<CompanyVerification> findByCompany_Account_Id(Long accountId);
    List<CompanyVerification> findByStatusInOrderBySubmittedAtAsc(List<CompanyVerificationStatus> statuses);
}
