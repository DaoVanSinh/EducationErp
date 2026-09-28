package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuditManagement audit;

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        accounts.save(new Account("flow@eduerp.local", passwordEncoder.encode("Password123!"), "Flow Test", null));

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("flow@eduerp.local", "Password123!"))))
                .andReturn();
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
        Cookie access = loginResult.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
        Cookie refresh = loginResult.getResponse().getCookie(IdentityConstants.Cookies.REFRESH_TOKEN);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();

        var refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refresh)).andReturn();
        assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);
        Cookie newAccess = refreshResult.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
        assertThat(newAccess.getValue()).isNotEqualTo(access.getValue());

        var logoutResult = mockMvc.perform(post("/api/auth/logout").cookie(newAccess).with(csrf())).andReturn();
        assertThat(logoutResult.getResponse().getStatus()).isEqualTo(200);

        var accountId = accounts.findByTypedEmail("flow@eduerp.local").orElseThrow().getId();
        // @ApplicationModuleListener chạy bất đồng bộ sau khi transaction của Login commit — đợi
        // thay vì đọc ngay, nếu không test sẽ chập chờn tuỳ tốc độ luồng nền ghi audit.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(audit.recentActions(AuditConstants.EntityTypes.ACCOUNT, AuditConstants.Actions.LOGIN, 20))
                        .extracting(a -> a.entityId())
                        .contains(accountId.toString()));
    }

    @Test
    void loginAcceptsTheEmailInAnyCase() throws Exception {
        accounts.save(new Account("Case.Insensitive@EduERP.Local", passwordEncoder.encode("Password123!"),
                "Case Insensitive", null));

        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("case.insensitive@eduerp.local", "Password123!"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refreshRejectsAnAccessTokenPresentedAsRefreshToken() throws Exception {
        accounts.save(new Account("type-confusion@eduerp.local", passwordEncoder.encode("Password123!"),
                "Type Confusion", null));

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("type-confusion@eduerp.local", "Password123!"))))
                .andReturn();
        Cookie access = loginResult.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);

        var result = mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(IdentityConstants.Cookies.REFRESH_TOKEN, access.getValue()))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutWithoutCsrfTokenIsRejected() throws Exception {
        accounts.save(new Account("csrf-logout@eduerp.local", passwordEncoder.encode("Password123!"),
                "Csrf Logout", null));

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("csrf-logout@eduerp.local", "Password123!"))))
                .andReturn();
        Cookie access = loginResult.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);

        var result = mockMvc.perform(post("/api/auth/logout").cookie(access)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
