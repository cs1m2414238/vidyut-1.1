package com.vidyut.marketplace.repository;

import com.vidyut.marketplace.entity.InstallationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstallationRequestRepository extends JpaRepository<InstallationRequest, Long> {
    List<InstallationRequest> findByHostUserIdOrderByUpdatedAtDesc(Long hostUserId);
    List<InstallationRequest> findByCompany_Account_IdOrderByUpdatedAtDesc(Long accountId);
    Optional<InstallationRequest> findByIdAndHostUserId(Long id, Long hostUserId);
    Optional<InstallationRequest> findByIdAndCompany_Account_Id(Long id, Long accountId);
}
