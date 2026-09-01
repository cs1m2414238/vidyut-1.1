package com.vidyut.admin.entity;

import com.vidyut.account.entity.Account;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccount {
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 150)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminRole adminRole;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime lastLoginAt;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
