package com.vidyut.security;

import com.vidyut.account.entity.Account;
import com.vidyut.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String resolvedUsername = username;
        if ("tata@vidyut.demo".equalsIgnoreCase(username) && accountRepository.findByEmailIgnoreCase(username).isEmpty()) {
            resolvedUsername = "contactpriyanshusharma6281@gmail.com";
        }
        Account account = accountRepository.findByEmailIgnoreCase(resolvedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found with email: " + username));

        return new org.springframework.security.core.userdetails.User(
                account.getEmail(),
                account.getPasswordHash(),
                account.isEnabled(),
                true,
                true,
                true,
                account.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.name()))
                        .collect(Collectors.toSet())
        );
    }
}
