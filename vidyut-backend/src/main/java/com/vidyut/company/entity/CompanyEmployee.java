package com.vidyut.company.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_employees", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "email"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEmployee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long companyId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false)
    private String email;
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeRole role;
    @Builder.Default
    private boolean active = true;
    @Column(length = 1000)
    private String permissions;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastActiveAt;
}
