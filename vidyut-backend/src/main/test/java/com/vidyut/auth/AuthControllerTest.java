package com.vidyut.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.auth.dto.RegisterCompanyRequest;
import com.vidyut.auth.dto.RegisterUserRequest;
import com.vidyut.auth.service.AuthService;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.wallet.repository.EvWalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired EvUserProfileRepository evUserProfileRepository;
    @Autowired EvWalletRepository walletRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthService authService;
    @Autowired NotificationService notificationService;

    @Test
    void credentialsArePersistedAndEvTokenCannotAccessCompanyMode() throws Exception {
        String email = uniqueEmail("ev");
        String password = "Password123!";
        String token = registerUser(email, password);

        var account = accountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(account.getRoles()).containsExactly(AccountRole.ROLE_EV_USER);
        assertThat(account.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, account.getPasswordHash())).isTrue();
        assertThat(evUserProfileRepository.findById(account.getId())).isPresent();
        assertThat(walletRepository.findByUserId(account.getId()))
                .get().extracting("balance").isEqualTo(0.0);

        mockMvc.perform(put("/api/auth/complete-profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"EV_USER","fullName":"Test Driver","phone":"9876543210"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.profileCompleted").value(true))
                .andExpect(jsonPath("$.data.user.phone").value("9876543210"));
        assertThat(evUserProfileRepository.findById(account.getId()).orElseThrow().getPhone())
                .isEqualTo("9876543210");

        mockMvc.perform(get("/api/ev/bookings").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ev/bookings/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
        mockMvc.perform(patch("/api/ev/bookings/mark-seen").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/company/profile").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void approvedIndividualCanSwitchBetweenEvAndHostMode() throws Exception {
        String email = uniqueEmail("dual");
        String evToken = registerUser(email, "Password123!");
        Long accountId = accountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        mockMvc.perform(post("/api/auth/host/apply")
                        .header("Authorization", bearer(evToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Dual Mode Driver\"}"))
                .andExpect(status().isOk());
        authService.approveHost(accountId);

        MvcResult switched = mockMvc.perform(post("/api/auth/switch-mode")
                        .header("Authorization", bearer(evToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"HOST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeMode").value("HOST"))
                .andExpect(jsonPath("$.data.user.allowedModes.length()").value(2))
                .andReturn();
        String hostToken = dataToken(switched);

        mockMvc.perform(get("/api/host/stations").header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/host/profile")
                        .header("Authorization", bearer(hostToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Dual Mode Driver","phone":"+919876543210",
                                 "address":"10 Host Lane","bio":"Private charger Host"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("9876543210"));

        notificationService.sendNotification(accountId, "Host test", "Unread Host update", NotificationType.SYSTEM_ALERT);
        MvcResult notifications = mockMvc.perform(get("/api/host/notifications")
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andReturn();
        long notificationId = objectMapper.readTree(notifications.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();
        mockMvc.perform(patch("/api/host/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(patch("/api/host/notifications/read-all")
                        .header("Authorization", bearer(hostToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ev/bookings").header("Authorization", bearer(hostToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ev/bookings").header("Authorization", bearer(evToken)))
                .andExpect(status().isOk());
    }

    @Test
    void companyAccountIsDisjointFromIndividualModes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RegisterCompanyRequest request = RegisterCompanyRequest.builder()
                .adminEmail("company-" + suffix + "@vidyut.test")
                .adminPassword("Password123!")
                .adminFullName("Company Operator")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register/company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeMode").value("COMPANY"))
                .andExpect(jsonPath("$.data.user.roles.length()").value(1))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ROLE_COMPANY"))
                .andExpect(jsonPath("$.data.user.profileCompleted").value(false))
                .andReturn();

        String token = dataToken(result);
        mockMvc.perform(put("/api/auth/complete-profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"COMPANY","fullName":"Company Operator","phone":"9123456789",
                                 "companyName":"Vidyut Company %s","registrationNumber":"CIN-%s"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.profileCompleted").value(true))
                .andExpect(jsonPath("$.data.user.companyName").value("Vidyut Company " + suffix));
        var company = companyRepository.findByAccount_Id(
                accountRepository.findByEmailIgnoreCase(request.getAdminEmail()).orElseThrow().getId()).orElseThrow();
        assertThat(company.getRegistrationNumber()).isEqualTo(("CIN-" + suffix).toUpperCase());

        mockMvc.perform(get("/api/company/profile").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/host/stations").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/switch-mode")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EV_USER\"}"))
                .andExpect(status().isForbidden());
    }

    private String registerUser(String email, String password) throws Exception {
        RegisterUserRequest request = RegisterUserRequest.builder()
                .email(email).password(password).fullName("Test Driver").build();
        MvcResult result = mockMvc.perform(post("/api/auth/register/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeMode").value("EV_USER"))
                .andExpect(jsonPath("$.data.user.role").value("ROLE_EV_USER"))
                .andReturn();
        return dataToken(result);
    }

    private String dataToken(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@vidyut.test";
    }
}
