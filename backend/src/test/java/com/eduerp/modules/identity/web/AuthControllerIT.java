package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Autowired
    AccessManagement access;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        var account = new Account("flow@eduerp.local", passwordEncoder.encode("Password123!"), "Flow Test", null);
        account.recordFirstLogin();
        accounts.save(account);

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
        var account = new Account("Case.Insensitive@EduERP.Local", passwordEncoder.encode("Password123!"),
                "Case Insensitive", null);
        account.recordFirstLogin();
        accounts.save(account);

        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("case.insensitive@eduerp.local", "Password123!"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refreshRejectsAnAccessTokenPresentedAsRefreshToken() throws Exception {
        var account = new Account("type-confusion@eduerp.local", passwordEncoder.encode("Password123!"),
                "Type Confusion", null);
        account.recordFirstLogin();
        accounts.save(account);

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
        var account = new Account("csrf-logout@eduerp.local", passwordEncoder.encode("Password123!"),
                "Csrf Logout", null);
        account.recordFirstLogin();
        accounts.save(account);

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("csrf-logout@eduerp.local", "Password123!"))))
                .andReturn();
        Cookie access = loginResult.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);

        var result = mockMvc.perform(post("/api/auth/logout").cookie(access)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void firstLoginWithInviteStillValidRequiresPasswordChange() throws Exception {
        var account = accounts.save(new Account("invitee@eduerp.local",
                passwordEncoder.encode("TempPass1!"), "Người mới", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.TEACHER);
        stringRedisTemplate.opsForValue().set("invite:account:" + account.getId(), "x",
                java.time.Duration.ofDays(7));

        var result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invitee@eduerp.local\",\"password\":\"TempPass1!\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"requiresPasswordChange\":true");
        assertThat(result.getResponse().getCookie("access_token")).isNull();
    }

    @Test
    void firstLoginWithExpiredInviteIsRejected() throws Exception {
        var account = accounts.save(new Account("expired-invitee@eduerp.local",
                passwordEncoder.encode("TempPass1!"), "Người mới", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.TEACHER);
        // Không set key Redis nào — mô phỏng lời mời đã hết hạn (TTL đã trôi qua).

        var result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"expired-invitee@eduerp.local\",\"password\":\"TempPass1!\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
