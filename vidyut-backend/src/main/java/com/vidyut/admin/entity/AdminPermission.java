package com.vidyut.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    private PermissionType permission;
}
