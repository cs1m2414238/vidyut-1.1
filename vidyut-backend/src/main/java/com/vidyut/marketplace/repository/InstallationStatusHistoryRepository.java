package com.vidyut.marketplace.repository;

import com.vidyut.marketplace.entity.InstallationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallationStatusHistoryRepository extends JpaRepository<InstallationStatusHistory, Long> {
    List<InstallationStatusHistory> findByRequest_IdOrderByCreatedAtAsc(Long requestId);
}
