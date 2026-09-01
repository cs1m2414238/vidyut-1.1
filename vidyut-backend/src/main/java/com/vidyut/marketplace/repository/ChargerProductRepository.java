package com.vidyut.marketplace.repository;

import com.vidyut.marketplace.entity.ChargerProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChargerProductRepository extends JpaRepository<ChargerProduct, Long> {
    List<ChargerProduct> findByCompany_Account_IdOrderByCreatedAtDesc(Long accountId);
    List<ChargerProduct> findByCompany_IdAndActiveTrueAndApprovalStatusOrderByPowerKwAsc(
            Long companyId, com.vidyut.marketplace.entity.ProductApprovalStatus approvalStatus);
    Optional<ChargerProduct> findByIdAndCompany_Account_Id(Long id, Long accountId);
}
