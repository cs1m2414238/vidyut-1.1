package com.vidyut.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_green_schemes", indexes = @Index(name = "idx_admin_green_scheme_status", columnList = "status,updated_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGreenScheme {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 220) private String name;
    @Column(nullable = false, length = 180) private String authority;
    @Column(nullable = false, length = 50) private String schemeType;
    @Column(length = 500) private String states;
    @Column(nullable = false, length = 1000) private String sourceUrl;
    @Column(nullable = false, length = 2000) private String summary;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private GreenSchemeStatus status;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private LocalDateTime lastVerifiedAt;
    private Long createdByAdminId;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
