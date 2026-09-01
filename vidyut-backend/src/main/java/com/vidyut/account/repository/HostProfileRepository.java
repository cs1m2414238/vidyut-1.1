package com.vidyut.account.repository;

import com.vidyut.account.entity.HostProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HostProfileRepository extends JpaRepository<HostProfile, Long> {
    List<HostProfile> findByVerifiedFalse();
}
