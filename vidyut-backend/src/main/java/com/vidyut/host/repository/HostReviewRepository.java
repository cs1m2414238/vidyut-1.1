package com.vidyut.host.repository;

import com.vidyut.host.entity.HostReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HostReviewRepository extends JpaRepository<HostReview, Long> {
    List<HostReview> findByHostAccountIdOrderByCreatedAtDesc(Long hostAccountId);
    Optional<HostReview> findByIdAndHostAccountId(Long id, Long hostAccountId);
}
