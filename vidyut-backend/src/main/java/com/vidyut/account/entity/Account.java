package com.vidyut.account.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "accounts")
@Check(constraints = "account_type in ('INDIVIDUAL','COMPANY','ADMIN')")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "role"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private Set<AccountRole> roles = new HashSet<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    void validatePartition() {
        updatedAt = LocalDateTime.now();
        if (accountType == null || roles == null || roles.isEmpty()) {
            throw new IllegalStateException("Every account must have an account type and at least one role");
        }

        boolean valid = switch (accountType) {
            case INDIVIDUAL -> roles.stream().allMatch(role ->
                    role == AccountRole.ROLE_EV_USER || role == AccountRole.ROLE_HOST);
            case COMPANY -> roles.equals(Set.of(AccountRole.ROLE_COMPANY));
            case ADMIN -> roles.equals(Set.of(AccountRole.ROLE_ADMIN));
        };

        if (!valid) {
            throw new IllegalStateException("Account roles violate the total/disjoint account partition");
        }
    }

    public boolean allows(AccessMode mode) {
        return roles.contains(mode.role());
    }
}
