package com.eduerp.modules.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.dashboard.dto.DashboardStatsResponse;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
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
    AccessManagement access;

    @Autowired
    BranchRepository branches;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, null));
        access.assignRole(account.getId(), roleCode);
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
        var admin = signIn("dashboard-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var adminId = accounts.findByTypedEmail("dashboard-admin@eduerp.local").orElseThrow().getId();

        // "Đăng nhập vừa xảy ra" tới dashboard qua sự kiện bất đồng bộ (audit lắng nghe
        // AccountSignedIn) — đợi tới khi nó xuất hiện thay vì gọi /stats ngay lập tức.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var result = mockMvc.perform(get("/api/dashboard/stats").cookie(admin)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            var stats = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardStatsResponse.class);
            assertThat(stats.recentLogins()).extracting(login -> login.accountId()).contains(adminId);
        });

        var result = mockMvc.perform(get("/api/dashboard/stats").cookie(admin)).andReturn();
        var stats = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardStatsResponse.class);
        assertThat(stats.totalAccounts()).isEqualTo(accounts.count());
        assertThat(stats.activeAccounts() + stats.disabledAccounts()).isEqualTo(stats.totalAccounts());
        assertThat(stats.branchCount()).isPositive();
        // Mọi role được seed đều phải có mặt, kể cả role chưa có tài khoản nào — LEFT JOIN chứ không INNER.
        assertThat(stats.accountsByRole()).extracting(DashboardStatsResponse.RoleHeadcount::roleCode)
                .contains(AccessConstants.RoleCodes.ADMIN, AccessConstants.RoleCodes.TEACHER,
                        AccessConstants.RoleCodes.STUDENT);
        assertThat(stats.accountsByRole()).allSatisfy(row -> assertThat(row.accountCount()).isNotNegative());
        assertThat(stats.recentLogins()).isNotEmpty().allSatisfy(login -> assertThat(login.occurredAt()).isNotNull());
    }

    /** Role thường có ACCOUNT:UPDATE nhưng không có DASHBOARD:READ — không được nhìn số liệu toàn hệ thống. */
    @Test
    void refusesARoleWithoutDashboardPermission() throws Exception {
        var teacher = signIn("dashboard-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(get("/api/dashboard/stats").cookie(teacher)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void refusesAnAnonymousCaller() throws Exception {
        var result = mockMvc.perform(get("/api/dashboard/stats")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
