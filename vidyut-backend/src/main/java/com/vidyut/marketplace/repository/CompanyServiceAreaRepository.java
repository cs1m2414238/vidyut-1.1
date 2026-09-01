package com.vidyut.marketplace.repository;

import com.vidyut.marketplace.entity.CompanyServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyServiceAreaRepository extends JpaRepository<CompanyServiceArea, Long> {
    List<CompanyServiceArea> findByCompany_Account_IdOrderByCityAsc(Long accountId);
    List<CompanyServiceArea> findByCompany_IdAndActiveTrue(Long companyId);
    Optional<CompanyServiceArea> findByIdAndCompany_Account_Id(Long id, Long accountId);
}
