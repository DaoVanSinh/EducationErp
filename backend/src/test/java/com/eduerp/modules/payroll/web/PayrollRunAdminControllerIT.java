package com.eduerp.modules.payroll.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.attendance.internal.model.AttendanceSession;
import com.eduerp.modules.attendance.internal.repository.AttendanceSessionRepository;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.courses.internal.model.ClassFixtures;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.RejectPayrollRunRequest;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.teachers.TeachersConstants.PayType;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

/** Kỳ lương qua HTTP thật: điều khoản lương trên hồ sơ giáo viên, giờ dạy từ buổi điểm danh thật trong DB. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PayrollRunAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String RUNS = "/api/payroll/runs";
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);

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

    @Autowired
    TeacherProfileRepository teacherProfiles;

    @Autowired
    AttendanceSessionRepository attendanceSessions;

    @Autowired
    BranchRepository branches;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

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

    private UUID newAccount(String prefix) {
        var account = new Account(prefix + "-" + shortId() + "@eduerp.local", passwordEncoder.encode(PASSWORD),
                "Giáo viên " + prefix, null);
        account.recordFirstLogin();
        return accounts.save(account).getId();
    }

    private UUID monthlyTeacher(String prefix, String salary) {
        var accountId = newAccount(prefix);
        var profile = new TeacherProfile(accountId, List.of(), null, null);
        profile.applyPayTerms(PayType.MONTHLY, new BigDecimal(salary), null, LONG_AGO, null, 0, List.of());
        teacherProfiles.saveAndFlush(profile);
        return accountId;
    }

    private UUID hourlyTeacher(String prefix, String rate) {
        var accountId = newAccount(prefix);
        var profile = new TeacherProfile(accountId, List.of(), null, null);
        profile.applyPayTerms(PayType.HOURLY, null, new BigDecimal(rate), LONG_AGO, null, 0, List.of());
        teacherProfiles.saveAndFlush(profile);
        return accountId;
    }

    /** Một lớp để gắn buổi điểm danh, giáo viên của lớp không ảnh hưởng: giờ tính theo teacherAccountId của buổi. */
    private UUID newClass(UUID teacherId) {
        var branchId = branches.save(new Branch("PR-" + shortId(), "Chi nhánh lương", null)).getId();
        var course = courses.saveAndFlush(new Course("PR-C-" + shortId(), "Khoá lương", null, 24, null));
        return classes.save(ClassFixtures.inProgress(course, "PR-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private void taught(UUID teacherId, UUID classId, LocalDate date, int minutes) {
        attendanceSessions.saveAndFlush(new AttendanceSession(classId, date, null, teacherId, teacherId, minutes));
    }

    private String createRun(Cookie admin, int year, int month) throws Exception {
        var result = mockMvc.perform(post(RUNS).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(year, month))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode detail(Cookie admin, String runId) throws Exception {
        var result = mockMvc.perform(get(RUNS + "/" + runId).cookie(admin)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode payslipOf(JsonNode detail, UUID accountId) {
        for (var node : detail.get("payslips")) {
            if (accountId.toString().equals(node.get("accountId").asText())) {
                return node;
            }
        }
        return null;
    }

    private int action(Cookie cookie, String runId, String action) throws Exception {
        return mockMvc.perform(post(RUNS + "/" + runId + "/" + action).cookie(cookie).with(csrf())).andReturn()
                .getResponse().getStatus();
    }

    @Test
    void runsTheFullFlowWithHoursFromAttendanceAndAuditsApproval() throws Exception {
        var admin = signIn("payroll-run-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var monthly = monthlyTeacher("full-month", "10000000");
        var hourly = hourlyTeacher("full-hour", "200000");
        var classId = newClass(hourly);
        taught(hourly, classId, LocalDate.of(2026, 1, 5), 120);
        taught(hourly, classId, LocalDate.of(2026, 1, 7), 90);
        taught(hourly, classId, LocalDate.of(2026, 2, 2), 600);

        var runId = createRun(admin, 2026, 1);
        var created = detail(admin, runId);

        assertThat(created.get("run").get("periodStart").asText()).isEqualTo("2026-01-01");
        assertThat(created.get("run").get("periodEnd").asText()).isEqualTo("2026-01-31");
        var hourlySlip = payslipOf(created, hourly);
        assertThat(hourlySlip.get("sessionCount").asInt()).as("không tính buổi của tháng khác").isEqualTo(2);
        assertThat(hourlySlip.get("hoursWorked").asDouble()).isEqualTo(3.5);
        assertThat(hourlySlip.get("grossPay").asDouble()).isEqualTo(700000.0);
        assertThat(hourlySlip.get("netPay").asDouble()).as("dưới ngưỡng khấu trừ").isEqualTo(700000.0);
        var monthlySlip = payslipOf(created, monthly);
        assertThat(monthlySlip.get("paidDays").asInt()).isEqualTo(31);
        assertThat(monthlySlip.get("netPay").asDouble()).as("10.000.000 - 10,5% BHXH").isEqualTo(8950000.0);

        assertThat(action(admin, runId, "submit")).isEqualTo(200);
        assertThat(action(admin, runId, "approve")).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.PAYROLL_RUN,
                    AuditConstants.Actions.PAYROLL_RUN_APPROVE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(runId);
        });
    }

    @Test
    void recalculatePicksUpAttendanceRecordedAfterTheRunWasCreatedAndKeepsTheAdjustment() throws Exception {
        var admin = signIn("payroll-run-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var hourly = hourlyTeacher("recalc", "100000");
        var classId = newClass(hourly);
        taught(hourly, classId, LocalDate.of(2026, 3, 2), 60);
        var runId = createRun(admin, 2026, 3);
        var payslipId = payslipOf(detail(admin, runId), hourly).get("id").asText();
        var update = mockMvc.perform(patch(RUNS + "/" + runId + "/payslips/" + payslipId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdatePayslipRequest(new BigDecimal("50000"), "Thưởng chuyên cần"))))
                .andReturn();
        assertThat(update.getResponse().getStatus()).isEqualTo(200);

        taught(hourly, classId, LocalDate.of(2026, 3, 4), 60);
        assertThat(action(admin, runId, "recalculate")).isEqualTo(200);

        var slip = payslipOf(detail(admin, runId), hourly);
        assertThat(slip.get("sessionCount").asInt()).isEqualTo(2);
        assertThat(slip.get("grossPay").asDouble()).isEqualTo(200000.0);
        assertThat(slip.get("adjustment").asDouble()).isEqualTo(50000.0);
        assertThat(slip.get("note").asText()).isEqualTo("Thưởng chuyên cần");
        assertThat(slip.get("netPay").asDouble()).isEqualTo(250000.0);
    }

    @Test
    void submittingAlwaysUsesTheLatestAttendance() throws Exception {
        var admin = signIn("payroll-run-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var hourly = hourlyTeacher("submit-latest", "100000");
        var classId = newClass(hourly);
        taught(hourly, classId, LocalDate.of(2026, 4, 1), 60);
        var runId = createRun(admin, 2026, 4);

        taught(hourly, classId, LocalDate.of(2026, 4, 2), 60);
        assertThat(action(admin, runId, "submit")).isEqualTo(200);

        assertThat(payslipOf(detail(admin, runId), hourly).get("sessionCount").asInt()).isEqualTo(2);
    }

    @Test
    void aTeacherWhoTaughtWithoutPayTermsBlocksSubmitUntilConfigured() throws Exception {
        var admin = signIn("payroll-run-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        monthlyTeacher("blocked-other", "10000000");
        var unpaid = newAccount("blocked-unpaid");
        var profile = teacherProfiles.saveAndFlush(new TeacherProfile(unpaid, List.of(), null, null));
        taught(unpaid, newClass(unpaid), LocalDate.of(2026, 5, 4), 120);
        var runId = createRun(admin, 2026, 5);

        var missing = detail(admin, runId).get("missingPayTerms");
        assertThat(missing).hasSize(1);
        assertThat(missing.get(0).get("accountId").asText()).isEqualTo(unpaid.toString());
        assertThat(missing.get(0).get("sessionCount").asInt()).isEqualTo(1);

        var blocked = mockMvc.perform(post(RUNS + "/" + runId + "/submit").cookie(admin).with(csrf())).andReturn();
        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(blocked.getResponse().getContentAsString()).contains("PAYROLL_MISSING_PAY_TERMS");

        profile.applyPayTerms(PayType.HOURLY, null, new BigDecimal("100000"), LONG_AGO, null, 0, List.of());
        teacherProfiles.saveAndFlush(profile);
        assertThat(action(admin, runId, "submit")).isEqualTo(200);
    }

    @Test
    void rejectSendsTheRunBackToDraftWithAVisibleReason() throws Exception {
        var admin = signIn("payroll-run-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        monthlyTeacher("reject", "10000000");
        var runId = createRun(admin, 2026, 6);
        assertThat(action(admin, runId, "submit")).isEqualTo(200);

        var rejectResult = mockMvc.perform(post(RUNS + "/" + runId + "/reject").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectPayrollRunRequest("Sai số liệu"))))
                .andReturn();
        assertThat(rejectResult.getResponse().getStatus()).isEqualTo(200);

        var run = detail(admin, runId).get("run");
        assertThat(run.get("status").asText()).isEqualTo("DRAFT");
        assertThat(run.get("rejectionReason").asText()).isEqualTo("Sai số liệu");
    }

    @Test
    void refusesAFuturePeriodAndADuplicatePeriod() throws Exception {
        var admin = signIn("payroll-run-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createRun(admin, 2026, 7);

        var duplicate = mockMvc.perform(post(RUNS).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2026, 7))))
                .andReturn();
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicate.getResponse().getContentAsString()).contains("PAYROLL_RUN_ALREADY_EXISTS");

        var future = mockMvc.perform(post(RUNS).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2099, 1))))
                .andReturn();
        assertThat(future.getResponse().getStatus()).isEqualTo(400);
        assertThat(future.getResponse().getContentAsString()).contains("PAYROLL_PERIOD_IN_FUTURE");
    }

    /** Khoá tài khoản đăng nhập và làm việc là hai chuyện độc lập: hồ sơ còn hoạt động thì vẫn tính lương. */
    @Test
    void stillPaysATeacherWhoseAccountWasDisabled() throws Exception {
        var admin = signIn("payroll-run-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherId = monthlyTeacher("disabled", "10000000");
        var account = accounts.findById(teacherId).orElseThrow();
        account.disable();
        accounts.save(account);

        var runId = createRun(admin, 2026, 8);

        assertThat(payslipOf(detail(admin, runId), teacherId)).isNotNull();
    }

    @Test
    void refusesApprovingWithoutApprovePermission() throws Exception {
        var admin = signIn("payroll-accountant@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("payroll-run-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        monthlyTeacher("no-approve", "10000000");
        var runId = createRun(admin, 2026, 9);
        assertThat(action(admin, runId, "submit")).isEqualTo(200);

        assertThat(action(outsider, runId, "approve")).isEqualTo(403);
    }
}
