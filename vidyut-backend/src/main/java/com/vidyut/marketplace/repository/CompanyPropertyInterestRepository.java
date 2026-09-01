package com.vidyut.marketplace.repository;

import com.vidyut.marketplace.entity.CompanyPropertyInterest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyPropertyInterestRepository extends JpaRepository<CompanyPropertyInterest, Long> {
    List<CompanyPropertyInterest> findByProperty_HostUserIdOrderByCreatedAtDesc(Long hostUserId);
    List<CompanyPropertyInterest> findByCompany_Account_IdOrderByCreatedAtDesc(Long accountId);
    Optional<CompanyPropertyInterest> findByCompany_IdAndProperty_Id(Long companyId, Long propertyId);
    Optional<CompanyPropertyInterest> findByIdAndProperty_HostUserId(Long id, Long hostUserId);
}
