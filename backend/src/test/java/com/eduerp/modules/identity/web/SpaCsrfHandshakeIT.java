package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Bắt tay CSRF đúng như trình duyệt làm: không dùng {@code csrf()} của Spring Test, mà đọc cookie
 * rồi gửi lại qua header. Các IT khác dùng {@code csrf()} nên sẽ xanh cả khi SPA thật không gửi nổi
 * một request nào.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SpaCsrfHandshakeIT {

    private static final String PASSWORD = "Password123!";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void handsTheBrowserACsrfCookieAndAcceptsItBackAsAHeader() throws Exception {
        var session = signIn("csrf-user@eduerp.local");
        var csrf = mockMvc.perform(get("/api/account/me").cookie(session))
                .andReturn().getResponse().getCookie(CSRF_COOKIE);

        assertThat(csrf).as("mọi phản hồi phải mang cookie CSRF về cho SPA").isNotNull();

        var accepted = mockMvc.perform(post("/api/auth/logout")
                        .cookie(session, csrf)
                        .header(CSRF_HEADER, csrf.getValue()))
                .andReturn();

        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refusesAMutationThatOnlyCarriesTheCookie() throws Exception {
        var session = signIn("csrf-forged@eduerp.local");
        var csrf = mockMvc.perform(get("/api/account/me").cookie(session))
                .andReturn().getResponse().getCookie(CSRF_COOKIE);

        var forged = mockMvc.perform(post("/api/auth/logout").cookie(session, csrf)).andReturn();

        assertThat(forged.getResponse().getStatus()).isEqualTo(403);
    }

    private Cookie signIn(String email) throws Exception {
        var account = accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), "Người dùng " + email, null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.ADMIN);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }
}
