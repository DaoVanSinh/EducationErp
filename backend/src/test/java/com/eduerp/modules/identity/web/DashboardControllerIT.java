package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.DashboardStatsResponse;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.model.Branch;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.BranchRepository;
import com.eduerp.modules.identity.internal.repository.RoleRepository;
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
class DashboardControllerIT {

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
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    BranchRepository branches;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var role = roles.findByCode(roleCode).orElseThrow();
        accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, role, null));
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @Test
    void reportsHeadcountsAndTheLoginThatJustHappened() throws Exception {
        branches.save(new Branch("DN01", "Chi nhánh Đà Nẵng", null));
        var admin = signIn("dashboard-admin@eduerp.local", IdentityConstants.RoleCodes.ADMIN);
        var adminId = accounts.findByTypedEmail("dashboard-admin@eduerp.local").orElseThrow().getId();

        var result = mockMvc.perform(get("/api/dashboard/stats").cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var stats = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardStatsResponse.class);
        assertThat(stats.totalAccounts()).isEqualTo(accounts.count());
        assertThat(stats.activeAccounts() + stats.disabledAccounts()).isEqualTo(stats.totalAccounts());
        assertThat(stats.branchCount()).isPositive();
        // Mọi role được seed đều phải có mặt, kể cả role chưa có tài khoản nào — LEFT JOIN chứ không INNER.
        assertThat(stats.accountsByRole()).extracting(DashboardStatsResponse.RoleHeadcount::roleCode)
                .contains(IdentityConstants.RoleCodes.ADMIN, IdentityConstants.RoleCodes.TEACHER,
                        IdentityConstants.RoleCodes.STUDENT);
        assertThat(stats.accountsByRole()).allSatisfy(row -> assertThat(row.accountCount()).isNotNegative());
        assertThat(stats.recentLogins()).isNotEmpty()
                .allSatisfy(login -> assertThat(login.occurredAt()).isNotNull())
                .extracting(login -> login.accountId()).contains(adminId);
    }

    /** Role thường có ACCOUNT:UPDATE nhưng không có DASHBOARD:READ — không được nhìn số liệu toàn hệ thống. */
    @Test
    void refusesARoleWithoutDashboardPermission() throws Exception {
        var teacher = signIn("dashboard-teacher@eduerp.local", IdentityConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(get("/api/dashboard/stats").cookie(teacher)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void refusesAnAnonymousCaller() throws Exception {
        var result = mockMvc.perform(get("/api/dashboard/stats")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
