package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Khoá / mở khoá / buộc đăng xuất (spec 6.3 Force-logout): hiệu lực phải là NGAY LẬP TỨC trên phiên đang
 * mở - không đợi access token 15 phút hết hạn, và refresh token 30 ngày không cứu được phiên đã bị thu hồi.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AccountLifecycleIT {

    private static final String PASSWORD = "Password123!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID activatedAccount(String email, String roleCode) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        return account.getId();
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
    }

    private Cookie accessCookie(MvcResult login) {
        return login.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private Cookie refreshCookie(MvcResult login) {
        return login.getResponse().getCookie(IdentityConstants.Cookies.REFRESH_TOKEN);
    }

    private int me(Cookie accessToken) throws Exception {
        return mockMvc.perform(get("/api/account/me").cookie(accessToken)).andReturn().getResponse().getStatus();
    }

    private int refresh(Cookie refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(refreshToken)).andReturn().getResponse().getStatus();
    }

    private int adminPost(Cookie admin, String path) throws Exception {
        return mockMvc.perform(post(path).cookie(admin).with(csrf())).andReturn().getResponse().getStatus();
    }

    @Test
    void disablingAnAccountKillsItsOpenSessionImmediatelyAndBlocksRefreshAndLogin() throws Exception {
        var admin = accessCookie(login(adminEmail("disable")));
        var studentId = activatedAccount("lifecycle-student@eduerp.local", AccessConstants.RoleCodes.STUDENT);
        var studentLogin = login("lifecycle-student@eduerp.local");
        assertThat(me(accessCookie(studentLogin))).isEqualTo(200);

        assertThat(adminPost(admin, "/api/rbac/accounts/" + studentId + "/disable")).isEqualTo(200);

        assertThat(me(accessCookie(studentLogin))).as("phiên đang mở chết ngay").isEqualTo(401);
        assertThat(refresh(refreshCookie(studentLogin))).as("refresh token không cứu được").isEqualTo(401);
        assertThat(login("lifecycle-student@eduerp.local").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void enablingADisabledAccountLetsItSignInAgain() throws Exception {
        var admin = accessCookie(login(adminEmail("enable")));
        var teacherId = activatedAccount("lifecycle-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        adminPost(admin, "/api/rbac/accounts/" + teacherId + "/disable");

        assertThat(adminPost(admin, "/api/rbac/accounts/" + teacherId + "/enable")).isEqualTo(200);

        assertThat(me(accessCookie(login("lifecycle-teacher@eduerp.local")))).isEqualTo(200);
    }

    @Test
    void signingOutEverywhereRevokesOpenSessionsButNotTheAccount() throws Exception {
        var admin = accessCookie(login(adminEmail("signout")));
        var teacherId = activatedAccount("lifecycle-signout@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var oldSession = login("lifecycle-signout@eduerp.local");

        assertThat(adminPost(admin, "/api/rbac/accounts/" + teacherId + "/sign-out-everywhere")).isEqualTo(200);

        assertThat(me(accessCookie(oldSession))).isEqualTo(401);
        assertThat(refresh(refreshCookie(oldSession))).isEqualTo(401);
        assertThat(me(accessCookie(login("lifecycle-signout@eduerp.local"))))
                .as("tài khoản không bị khoá - đăng nhập lại được ngay sau mốc thu hồi")
                .isEqualTo(200);
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() throws Exception {
        var email = adminEmail("self");
        var admin = accessCookie(login(email));
        var adminId = accounts.findByTypedEmail(email).orElseThrow().getId();

        assertThat(adminPost(admin, "/api/rbac/accounts/" + adminId + "/disable")).isEqualTo(409);
        assertThat(me(admin)).isEqualTo(200);
    }

    @Test
    void anAccountThatNeverActivatedIsReopenedByResendingTheInviteNotByEnabling() throws Exception {
        var admin = accessCookie(login(adminEmail("never")));
        var pending = accounts.save(new Account("lifecycle-pending@eduerp.local", "hash", "Chưa kích hoạt", null));

        assertThat(adminPost(admin, "/api/rbac/accounts/" + pending.getId() + "/enable")).isEqualTo(409);
    }

    @Test
    void aPersonalScopeAccountCannotDisableAnyone() throws Exception {
        var teacher = activatedAccount("lifecycle-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var victim = activatedAccount("lifecycle-victim@eduerp.local", AccessConstants.RoleCodes.STUDENT);
        var teacherCookie = accessCookie(login("lifecycle-outsider@eduerp.local"));

        assertThat(adminPost(teacherCookie, "/api/rbac/accounts/" + victim + "/disable")).isEqualTo(403);
        assertThat(accounts.findById(victim).orElseThrow().getStatus())
                .isEqualTo(IdentityConstants.AccountStatus.ACTIVE);
        assertThat(teacher).isNotNull();
    }

    private String adminEmail(String suffix) {
        var email = "lifecycle-admin-" + suffix + "@eduerp.local";
        activatedAccount(email, AccessConstants.RoleCodes.ADMIN);
        return email;
    }
}
