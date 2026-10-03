package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.ChangePasswordRequest;
import com.eduerp.modules.identity.dto.ForgotPasswordRequest;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.ProfileUpdateRequest;
import com.eduerp.modules.identity.dto.ResetPasswordRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "management.health.mail.enabled=false")
class AccountSelfServiceControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @MockBean
    JavaMailSender mailSender;

    /** MailClient giờ dựng MimeMessage thật (email HTML) — mock trả về null nếu không stub việc này. */
    @BeforeEach
    void mockMailSenderCreatesARealMimeMessage() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate redisTemplate;

    private Cookie login(String email, String rawPassword) throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, rawPassword))))
                .andReturn();
        return loginResult.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @Test
    void changePasswordThenLoginWithNewPassword() throws Exception {
        var account = new Account("selfservice@eduerp.local", passwordEncoder.encode("OldPass123!"), "Self Service",
                null);
        account.recordFirstLogin();
        accounts.save(account);
        var access = login("selfservice@eduerp.local", "OldPass123!");

        var result = mockMvc.perform(post("/api/account/change-password")
                        .cookie(access)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPass123!", "NewPass456!"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(login("selfservice@eduerp.local", "NewPass456!")).isNotNull();
    }

    @Test
    void changePasswordWithoutCsrfTokenIsRejected() throws Exception {
        var account = new Account("csrf-guard@eduerp.local", passwordEncoder.encode("OldPass123!"), "Csrf Guard",
                null);
        account.recordFirstLogin();
        accounts.save(account);
        var access = login("csrf-guard@eduerp.local", "OldPass123!");

        var result = mockMvc.perform(post("/api/account/change-password")
                        .cookie(access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPass123!", "NewPass456!"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void forgotPasswordThenResetPassword() throws Exception {
        var account = new Account("forgot@eduerp.local", passwordEncoder.encode("Whatever123!"), "Forgot Test",
                null);
        account.recordFirstLogin();
        accounts.save(account);

        mockMvc.perform(post("/api/account/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest("forgot@eduerp.local"))));

        String token = redisTemplate.keys(CacheKeyBuilder.pattern(IdentityConstants.CacheNamespaces.PASSWORD_RESET_TOKEN)).stream().findFirst()
                .map(key -> CacheKeyBuilder.identifierIn(IdentityConstants.CacheNamespaces.PASSWORD_RESET_TOKEN, key))
                .orElseThrow();

        var resetResult = mockMvc.perform(post("/api/account/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(token, "BrandNew789!"))))
                .andReturn();

        assertThat(resetResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(login("forgot@eduerp.local", "BrandNew789!")).isNotNull();
    }

    @Test
    void updatesProfile() throws Exception {
        var account = new Account("profile@eduerp.local", passwordEncoder.encode("Password123!"), "Old Name",
                null);
        account.recordFirstLogin();
        accounts.save(account);
        var access = login("profile@eduerp.local", "Password123!");

        var result = mockMvc.perform(patch("/api/account/profile")
                        .cookie(access)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProfileUpdateRequest("New Name", "https://cdn/avatar.png"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accounts.findByEmail("profile@eduerp.local").orElseThrow().getFullName()).isEqualTo("New Name");
    }
}
