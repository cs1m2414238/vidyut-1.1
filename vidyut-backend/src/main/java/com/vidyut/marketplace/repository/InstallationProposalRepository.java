package com.vidyut.marketplace.repository;

import com.vidyut.marketplace.entity.InstallationProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstallationProposalRepository extends JpaRepository<InstallationProposal, Long> {
    Optional<InstallationProposal> findByRequest_Id(Long requestId);
}
