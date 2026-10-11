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
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
 * Khẳng định quan trọng nhất của cả hai endpoint: một tài khoản KHÔNG có READ_PAYROLL (ở đây là
 * STUDENT, vai trò chắc chắn không được cấp quyền lương/hợp đồng - spec Phân hệ 2.6) vẫn xem được
 * ĐÚNG hợp đồng/phiếu lương CỦA CHÍNH MÌNH, và không thấy của người khác - mirror
 * {@code AccountSelfServiceController.me()} ("ai đăng nhập được thì đương nhiên được biết mình là
 * ai"), áp dụng cho dữ liệu lương/hợp đồng thay vì chỉ thông tin tài khoản.
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
    EmploymentContractRepository contracts;

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
                passwordEncoder.encode(PASSWORD), "Học viên " + emailPrefix, null);
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

    @Test
    void returnsMyOwnContractsWithoutRequiringThePayrollPermission() throws Exception {
        var mine = newAccount("psc-mine");
        var someoneElse = newAccount("psc-other");
        contracts.saveAndFlush(new EmploymentContract(mine, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("15000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of()));
        contracts.saveAndFlush(new EmploymentContract(someoneElse, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("20000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of()));
        var student = signIn(mine);

        var result = mockMvc.perform(get("/api/payroll/me/contracts").cookie(student)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("accountId").asText()).isEqualTo(mine.toString());
        assertThat(items.get(0).get("baseSalary").asDouble()).isEqualTo(15000000.0);
    }

    @Test
    void returnsMyOwnPayslipsWithoutRequiringThePayrollPermission() throws Exception {
        var mine = newAccount("psp-mine");
        var someoneElse = newAccount("psp-other");
        var run = payrollRuns.saveAndFlush(new PayrollRun(2026, 3));
        payslips.saveAndFlush(new Payslip(run, mine, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("15000000"), new BigDecimal("500000"), new BigDecimal("1500000"),
                new BigDecimal("1500000"), null, false));
        payslips.saveAndFlush(new Payslip(run, someoneElse, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("20000000"), new BigDecimal("0"), new BigDecimal("2000000"),
                new BigDecimal("2000000"), null, false));
        var student = signIn(mine);

        var result = mockMvc.perform(get("/api/payroll/me/payslips").cookie(student)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        var row = items.get(0);
        assertThat(row.get("year").asInt()).isEqualTo(2026);
        assertThat(row.get("month").asInt()).isEqualTo(3);
        assertThat(row.get("netPay").asDouble())
                .as("15.000.000 + 500.000 - 1.500.000 = 14.000.000")
                .isEqualTo(14000000.0);
    }

    @Test
    void refusesAnAnonymousCaller() throws Exception {
        assertThat(mockMvc.perform(get("/api/payroll/me/contracts")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
        assertThat(mockMvc.perform(get("/api/payroll/me/payslips")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
    }
}
