package com.eduerp.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.identity.IdentityConstants;
import com.eduerp.identity.dto.LoginRequest;
import com.eduerp.identity.internal.model.Account;
import com.eduerp.identity.internal.repository.AccountRepository;
import com.eduerp.identity.internal.repository.RoleRepository;
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
    RoleRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginRefreshLogoutFlow() throws Exception {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("flow@eduerp.local", passwordEncoder.encode("Password123!"), "Flow Test", role, null));

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
    }

    @Test
    void refreshRejectsAnAccessTokenPresentedAsRefreshToken() throws Exception {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("type-confusion@eduerp.local", passwordEncoder.encode("Password123!"),
                "Type Confusion", role, null));

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
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        accounts.save(new Account("csrf-logout@eduerp.local", passwordEncoder.encode("Password123!"),
                "Csrf Logout", role, null));

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
