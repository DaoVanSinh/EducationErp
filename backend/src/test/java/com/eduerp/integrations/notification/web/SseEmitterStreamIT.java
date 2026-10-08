package com.eduerp.integrations.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.integrations.notification.NotificationManagement;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
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
class SseEmitterStreamIT {

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

    @Autowired
    NotificationManagement notifications;

    private Cookie signIn(String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var saved = accounts.save(account);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    @Test
    void onlyTheSubscribedAccountReceivesItsPush() throws Exception {
        var cookieA = signIn("sse-account-a@eduerp.local");
        var cookieB = signIn("sse-account-b@eduerp.local");

        var streamA = mockMvc.perform(get("/api/notifications/stream").cookie(cookieA)).andReturn();
        var streamB = mockMvc.perform(get("/api/notifications/stream").cookie(cookieB)).andReturn();

        var accountAId = accounts.findByTypedEmail("sse-account-a@eduerp.local").orElseThrow().getId();
        notifications.push(accountAId, "PERMISSION_CHANGED", null);

        // SseEmitter là stream sống lâu, không "hoàn tất" như Callable/DeferredResult - không gọi
        // asyncDispatch() (nó chờ async xử lý xong, mà emitter thì cố tình không bao giờ xong ở đây).
        // MockHttpServletResponse đã ghi nhận byte ngay khi emitter.send() gọi tới, đọc thẳng là đủ.
        var bodyA = streamA.getResponse().getContentAsString();
        assertThat(bodyA).contains("PERMISSION_CHANGED");

        var bodyB = streamB.getResponse().getContentAsString();
        assertThat(bodyB).doesNotContain("PERMISSION_CHANGED");
    }
}
