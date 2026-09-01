package com.vidyut.account.repository;

import com.vidyut.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findByGoogleSubject(String googleSubject);
    boolean existsByEmailIgnoreCase(String email);
}
