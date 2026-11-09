package com.eduerp.modules.payroll.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Khẳng định quan trọng nhất: một tài khoản KHÔNG có READ_PAYROLL vẫn xem được ĐÚNG phiếu lương CỦA CHÍNH MÌNH, chỉ
 * từ kỳ đã duyệt, và không thấy của người khác.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PayrollSelfServiceControllerIT {

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
    PayrollRunRepository payrollRuns;

    @Autowired
    PayslipRepository payslips;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String emailPrefix) {
        var account = new Account(emailPrefix + "-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Tài khoản " + emailPrefix, null);
        account.recordFirstLogin();
        return accounts.save(account).getId();
    }

    private Cookie signIn(UUID accountId) throws Exception {
        var account = accounts.findById(accountId).orElseThrow();
        access.assignRole(accountId, AccessConstants.RoleCodes.STUDENT);
        access.evictPermissionCache(accountId);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private PayrollRun approvedRun(int year, int month) {
        var run = new PayrollRun(year, month);
        run.approve();
        return payrollRuns.saveAndFlush(run);
    }

    @Test
    void returnsOnlyMyOwnPayslipsFromApprovedRuns() throws Exception {
        var mine = newAccount("psp-mine");
        var someoneElse = newAccount("psp-other");
        var approved = approvedRun(2026, 3);
        var stillDraft = payrollRuns.saveAndFlush(new PayrollRun(2026, 4));
        payslips.saveAndFlush(PayslipFixtures.monthly(approved, mine, 10_000_000));
        payslips.saveAndFlush(PayslipFixtures.monthly(stillDraft, mine, 99_000_000));
        payslips.saveAndFlush(PayslipFixtures.monthly(approved, someoneElse, 20_000_000));
        var student = signIn(mine);

        var result = mockMvc.perform(get("/api/payroll/me/payslips").cookie(student)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        var row = items.get(0);
        assertThat(row.get("year").asInt()).isEqualTo(2026);
        assertThat(row.get("month").asInt()).isEqualTo(3);
        assertThat(row.get("periodEnd").asText()).isEqualTo("2026-03-31");
        assertThat(row.get("payType").asText()).isEqualTo("MONTHLY");
        assertThat(row.get("netPay").asDouble()).as("10.000.000 - 10,5% BHXH").isEqualTo(8950000.0);
    }

    @Test
    void refusesAnAnonymousCaller() throws Exception {
        assertThat(mockMvc.perform(get("/api/payroll/me/payslips")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
    }
}
