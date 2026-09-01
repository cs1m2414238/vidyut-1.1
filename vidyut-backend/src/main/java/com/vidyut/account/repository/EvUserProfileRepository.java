package com.vidyut.account.repository;

import com.vidyut.account.entity.EvUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvUserProfileRepository extends JpaRepository<EvUserProfile, Long> {
}
