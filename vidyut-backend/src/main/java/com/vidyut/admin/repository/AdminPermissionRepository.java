package com.vidyut.admin.repository;

import com.vidyut.admin.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {
    List<AdminPermission> findByAdminUserId(Long adminUserId);
}
