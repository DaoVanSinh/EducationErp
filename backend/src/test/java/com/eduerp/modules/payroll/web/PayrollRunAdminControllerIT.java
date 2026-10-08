package com.eduerp.modules.payroll.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.RejectPayrollRunRequest;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
class PayrollRunAdminControllerIT {

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
    AuditManagement audit;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private UUID createOfficialContract(Cookie admin, String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var accountId = accounts.save(account).getId();
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/payroll/contracts")
                        .file(new org.springframework.mock.web.MockMultipartFile("request", "",
                                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()));
        return accountId;
    }

    private UUID createCollaboratorContract(Cookie admin, String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var accountId = accounts.save(account).getId();
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2026, 1, 1), List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/payroll/contracts")
                        .file(new org.springframework.mock.web.MockMultipartFile("request", "",
                                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()));
        return accountId;
    }

    @Test
    void runsTheFullDraftToApprovedFlowAndAuditsApproval() throws Exception {
        var admin = signIn("payroll-run-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createOfficialContract(admin, "run-official-1@eduerp.local");
        var collaboratorAccountId = createCollaboratorContract(admin, "run-collaborator-1@eduerp.local");

        var createResult = mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 1))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var runId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var detailResult = mockMvc.perform(get("/api/payroll/runs/" + runId).cookie(admin)).andReturn();
        var payslips = objectMapper.readTree(detailResult.getResponse().getContentAsString()).get("payslips");
        String collaboratorPayslipId = null;
        for (var node : payslips) {
            if (collaboratorAccountId.toString().equals(node.get("accountId").asText())) {
                collaboratorPayslipId = node.get("id").asText();
            }
        }
        assertThat(collaboratorPayslipId).isNotNull();

        // Review Focus mục 3.3 bước 4 ở tầng HTTP: chưa nhập giờ CTV thì submit bị chặn 400.
        var earlySubmit = mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(earlySubmit.getResponse().getStatus()).isEqualTo(400);

        mockMvc.perform(patch("/api/payroll/runs/" + runId + "/payslips/" + collaboratorPayslipId)
                .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdatePayslipRequest(new BigDecimal("20"),
                        BigDecimal.ZERO, "Đã dạy đủ giờ"))));

        var submitResult = mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(submitResult.getResponse().getStatus()).isEqualTo(200);

        var approveResult = mockMvc.perform(post("/api/payroll/runs/" + runId + "/approve").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(approveResult.getResponse().getStatus()).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.PAYROLL_RUN,
                    AuditConstants.Actions.PAYROLL_RUN_APPROVE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(runId);
        });
    }

    @Test
    void rejectSendsTheRunBackToDraftWithAReason() throws Exception {
        var admin = signIn("payroll-run-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createOfficialContract(admin, "run-official-2@eduerp.local");
        mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 2))));
        var listResult = mockMvc.perform(get("/api/payroll/runs").cookie(admin)).andReturn();
        var runId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items").get(0)
                .get("id").asText();
        mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(admin).with(csrf()));

        var rejectResult = mockMvc.perform(post("/api/payroll/runs/" + runId + "/reject").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectPayrollRunRequest("Sai số liệu"))))
                .andReturn();

        assertThat(rejectResult.getResponse().getStatus()).isEqualTo(200);
    }

    /** Review Focus #4 ở tầng HTTP. */
    @Test
    void rejectsADuplicatePeriodAtHttpLevel() throws Exception {
        var admin = signIn("payroll-run-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 3))));

        var duplicateResult = mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 3))))
                .andReturn();

        assertThat(duplicateResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicateResult.getResponse().getContentAsString()).contains("PAYROLL_RUN_ALREADY_EXISTS");
    }

    /** Review Focus #5 ở tầng HTTP đầy đủ: khoá tài khoản (DISABLED) rồi tạo kỳ lương, hợp đồng
     * ACTIVE của tài khoản đó vẫn được tính lương. */
    @Test
    void stillPaysAnEmployeeWhoseAccountWasDisabled() throws Exception {
        var admin = signIn("payroll-run-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = createOfficialContract(admin, "run-disabled-1@eduerp.local");
        var employeeAccount = accounts.findById(employeeId).orElseThrow();
        employeeAccount.disable();
        accounts.save(employeeAccount);

        var createResult = mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 4))))
                .andReturn();
        var runId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var detailResult = mockMvc.perform(get("/api/payroll/runs/" + runId).cookie(admin)).andReturn();
        var payslips = objectMapper.readTree(detailResult.getResponse().getContentAsString()).get("payslips");
        var hasDisabledEmployeePayslip = false;
        for (var node : payslips) {
            if (employeeId.toString().equals(node.get("accountId").asText())) {
                hasDisabledEmployeePayslip = true;
            }
        }
        assertThat(hasDisabledEmployeePayslip).isTrue();
    }

    @Test
    void refusesApprovingWithoutApprovePermission() throws Exception {
        var accountant = signIn("payroll-accountant@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        // Lưu ý: trong hệ thống thật, "Kế toán" là một role tự tạo qua màn RBAC chỉ có
        // CREATE+READ+UPDATE (không APPROVE) - test này dùng trực tiếp account không có quyền nào
        // (TEACHER) để khẳng định @PreAuthorize chặn đúng, không phụ thuộc việc seed thêm role demo.
        var outsider = signIn("payroll-run-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        mockMvc.perform(post("/api/payroll/runs").cookie(accountant).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 5))));
        var listResult = mockMvc.perform(get("/api/payroll/runs").cookie(accountant)).andReturn();
        var runId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items").get(0)
                .get("id").asText();
        mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(accountant).with(csrf()));

        var result = mockMvc.perform(post("/api/payroll/runs/" + runId + "/approve").cookie(outsider).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
