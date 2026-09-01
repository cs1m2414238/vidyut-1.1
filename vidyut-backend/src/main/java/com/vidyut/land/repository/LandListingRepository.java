package com.vidyut.land.repository;

import com.vidyut.land.entity.LandListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LandListingRepository extends JpaRepository<LandListing, Long> {
    List<LandListing> findByHostUserId(Long hostUserId);
    Optional<LandListing> findByIdAndHostUserId(Long id, Long hostUserId);
    Optional<LandListing> findByHostUserIdAndTitle(Long hostUserId, String title);
    List<LandListing> findByDiscoverableTrueAndStatusIn(List<com.vidyut.land.entity.LandListingStatus> statuses);
}
