package com.vidyut.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.*;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.admin.entity.AdminAccount;
import com.vidyut.admin.entity.AdminRole;
import com.vidyut.admin.repository.AdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuthenticationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired AdminAccountRepository adminRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void administratorUsesOnlyTheSeparateAdminLogin() throws Exception {
        String password = "AdminSecurity123!";
        Account account = accountRepository.save(Account.builder().email("security.admin@test.local")
                .passwordHash(passwordEncoder.encode(password)).accountType(AccountType.ADMIN)
                .roles(new HashSet<>(Set.of(AccountRole.ROLE_ADMIN))).enabled(true).emailVerified(true).build());
        adminRepository.save(AdminAccount.builder().account(account).displayName("Security Admin")
                .adminRole(AdminRole.SUPER_ADMIN).active(true).build());
        String payload = objectMapper.writeValueAsString(Map.of("email", account.getEmail(), "password", password));

        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.admin.role").value("SUPER_ADMIN"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Admin Portal")));
    }
}
