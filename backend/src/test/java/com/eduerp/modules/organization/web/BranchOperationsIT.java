package com.eduerp.modules.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.ClassFixtures;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.students.StudentsManagement;
import com.eduerp.modules.teachers.TeacherFixtures;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Các luật vận hành theo chuỗi chi nhánh, đi qua HTTP thật: đóng tài khoản và dùng lại email, quyền của admin chi
 * nhánh, hạn học phí 7 ngày, gia hạn, thay giáo viên theo ngày, và chi trả lương theo chi nhánh.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "management.health.mail.enabled=false")
class BranchOperationsIT {

    private static final String PASSWORD = "Password123!";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final BigDecimal TUITION = new BigDecimal("6000000");
    private static final List<WeeklyScheduleSlot> MON_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AccountRepository accounts;
    @Autowired
    AccessManagement access;
    @Autowired
    BranchRepository branches;
    @Autowired
    CourseRepository courses;
    @Autowired
    ClassRepository classes;
    @Autowired
    EnrollmentRepository enrollments;
    @Autowired
    StudentsManagement students;
    @Autowired
    TeacherProfileRepository teacherProfiles;
    @Autowired
    InvoiceRepository invoices;
    @Autowired
    PayrollRunRepository payrollRuns;
    @Autowired
    PayslipRepository payslips;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockBean
    JavaMailSender mailSender;

    private String suffix;
    private UUID branchA;
    private UUID branchB;
    private Cookie root;
    private Cookie adminOfA;

    @BeforeEach
    void seed() throws Exception {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        branchA = branches.save(new Branch("OA-" + suffix, "Chi nhánh A " + suffix, null)).getId();
        branchB = branches.save(new Branch("OB-" + suffix, "Chi nhánh B " + suffix, null)).getId();
        root = signIn(account("root", AccessConstants.RoleCodes.ADMIN, null));
        adminOfA = signIn(account("admin-a", AccessConstants.RoleCodes.BRANCH_ADMIN, branchA));
    }

    private String email(String name) {
        return name + "-" + suffix + "@eduerp.local";
    }

    private UUID account(String name, String roleCode, UUID homeBranchId) {
        var account = new Account(email(name), passwordEncoder.encode(PASSWORD), name, homeBranchId);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        return account.getId();
    }

    private Cookie signIn(UUID accountId) throws Exception {
        return login(accounts.findById(accountId).orElseThrow().getEmail()).getResponse()
                .getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))).andReturn();
    }

    private UUID teacher(String name, UUID branchId) {
        var id = account(name, AccessConstants.RoleCodes.TEACHER, branchId);
        TeacherFixtures.makeAssignable(teacherProfiles, jdbc, id);
        return id;
    }

    private Course course(String code) {
        return courses.save(new Course(code + "-" + suffix, "Khoá " + code, null, 24, TUITION));
    }

    private UUID enrollingClass(String code, UUID branchId, UUID teacherId, LocalDate startDate) throws Exception {
        var result = send(root, "/api/courses/classes", new CreateClassRequest(course(code).getId(),
                code + "-" + suffix, branchId, teacherId, 20, 1, startDate, null, MON_EVENING));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    private String enroll(UUID studentAccountId, UUID classId) throws Exception {
        var profileId = students.findProfileByAccountId(studentAccountId).orElseThrow().studentProfileId();
        var result = send(root, "/api/enrollment/enrollments", Map.of("studentProfileId", profileId, "classId", classId));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult send(Cookie cookie, String path, Object body) throws Exception {
        var request = post(path).cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        return mockMvc.perform(body == null ? request : request.content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private MvcResult patchClass(UUID classId, UpdateClassRequest request) throws Exception {
        return mockMvc.perform(patch("/api/courses/classes/" + classId).cookie(root).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andReturn();
    }

    private JsonNode json(Cookie cookie, String path) throws Exception {
        var result = mockMvc.perform(get(path).cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode invoiceOf(String enrollmentId) throws Exception {
        return json(root, "/api/billing/invoices?enrollmentId=" + enrollmentId).get("items").get(0);
    }

    private int close(Cookie actor, UUID accountId) throws Exception {
        return send(actor, "/api/rbac/accounts/" + accountId + "/close", null).getResponse().getStatus();
    }

    // ---------- Đóng tài khoản

    @Test
    void aClosedAccountCannotSignInAndFreesItsEmailForANewAccount() throws Exception {
        var staff = account("staff", AccessConstants.RoleCodes.FORMER_STAFF, branchA);

        assertThat(close(root, staff)).isEqualTo(200);

        assertThat(accounts.findById(staff).orElseThrow().getStatus())
                .isEqualTo(IdentityConstants.AccountStatus.CLOSED);
        assertThat(login(email("staff")).getResponse().getStatus()).isNotEqualTo(200);

        var request = new CreateAccountRequest(email("staff"), "Quay lại làm", branchA,
                access.roleIdOf(AccessConstants.RoleCodes.FORMER_STAFF));
        var recreated = send(root, "/api/rbac/accounts", request);
        assertThat(recreated.getResponse().getStatus()).isEqualTo(200);
        var newId = objectMapper.readValue(recreated.getResponse().getContentAsString(), UUID.class);
        assertThat(newId).isNotEqualTo(staff);
        assertThat(accounts.findByTypedEmail(email("staff"))).map(Account::getId).contains(newId);

        // Một email chỉ có một tài khoản chưa đóng.
        assertThat(send(root, "/api/rbac/accounts", request).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void aTeacherWithAnOpenClassOrAStudentStillStudyingCannotBeClosed() throws Exception {
        var teacher = teacher("t-busy", branchA);
        var classId = enrollingClass("BUSY", branchA, teacher, TODAY.plusDays(10));
        var student = account("s-busy", AccessConstants.RoleCodes.STUDENT, branchA);
        enroll(student, classId);

        var teacherResult = send(root, "/api/rbac/accounts/" + teacher + "/close", null);
        assertThat(teacherResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(teacherResult.getResponse().getContentAsString()).contains("COURSES_TEACHER_HAS_OPEN_CLASSES");

        var studentResult = send(root, "/api/rbac/accounts/" + student + "/close", null);
        assertThat(studentResult.getResponse().getStatus()).isEqualTo(409);
        // Học viên này vừa đang học vừa còn nợ: lý do nào được báo trước cũng đúng.
        assertThat(studentResult.getResponse().getContentAsString()).containsAnyOf(
                "ENROLLMENT_STUDENT_HAS_ACTIVE_ENROLLMENTS", "BILLING_STUDENT_HAS_UNPAID_INVOICES");
    }

    @Test
    void unpaidTuitionBlocksClosingUntilItIsPaid() throws Exception {
        var classId = enrollingClass("DEBT", branchA, teacher("t-debt", branchA), TODAY.plusDays(10));
        var student = account("s-debt", AccessConstants.RoleCodes.STUDENT, branchA);
        var enrollmentId = enroll(student, classId);
        jdbc.update("UPDATE enrollments SET status = 'COMPLETED' WHERE id = ?", UUID.fromString(enrollmentId));

        var refused = send(root, "/api/rbac/accounts/" + student + "/close", null);
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(refused.getResponse().getContentAsString()).contains("BILLING_STUDENT_HAS_UNPAID_INVOICES");

        var invoice = invoiceOf(enrollmentId);
        assertThat(send(root, "/api/billing/invoices/" + invoice.get("id").asText() + "/manual-payment",
                Map.of("amount", TUITION)).getResponse().getStatus()).isEqualTo(200);
        assertThat(close(root, student)).isEqualTo(200);
    }

    @Test
    void aBranchAdminClosesOnlyOrdinaryAccountsOfTheirOwnBranch() throws Exception {
        assertThat(close(adminOfA, account("own", AccessConstants.RoleCodes.FORMER_STAFF, branchA))).isEqualTo(200);
        assertThat(close(adminOfA, account("foreign", AccessConstants.RoleCodes.FORMER_STAFF, branchB))).isEqualTo(403);

        var peer = send(adminOfA, "/api/rbac/accounts/"
                + account("peer", AccessConstants.RoleCodes.BRANCH_ADMIN, branchA) + "/close", null);
        assertThat(peer.getResponse().getStatus()).isEqualTo(403);
        assertThat(peer.getResponse().getContentAsString()).contains("IDENTITY_ACCOUNT_PROTECTED");
    }

    @Test
    void onlyTheRootAdminHandlesABranchAdminAndNobodyClosesARootAdmin() throws Exception {
        assertThat(close(root, account("former-boss", AccessConstants.RoleCodes.BRANCH_ADMIN, branchA))).isEqualTo(200);

        var otherRoot = send(root, "/api/rbac/accounts/"
                + account("root-2", AccessConstants.RoleCodes.ADMIN, null) + "/close", null);
        assertThat(otherRoot.getResponse().getStatus()).isEqualTo(403);
        assertThat(otherRoot.getResponse().getContentAsString()).contains("IDENTITY_ACCOUNT_PROTECTED");
    }

    @Test
    void aBranchAdminCannotChangeRolesOrTransferBranches() throws Exception {
        var staff = account("no-role-change", AccessConstants.RoleCodes.FORMER_STAFF, branchA);

        assertThat(mockMvc.perform(patch("/api/rbac/accounts/" + staff + "/role").cookie(adminOfA).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":\"%s\"}".formatted(access.roleIdOf(AccessConstants.RoleCodes.BRANCH_ADMIN))))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(send(adminOfA, "/api/rbac/accounts/" + staff + "/transfer-branch", Map.of("branchId", branchB))
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void accountListFiltersByRoleBranchStatusAndTextAndABranchAdminStaysInTheirBranch() throws Exception {
        var teacherA = teacher("list-t-a", branchA);
        var teacherB = teacher("list-t-b", branchB);
        var studentA = account("list-s-a", AccessConstants.RoleCodes.STUDENT, branchA);

        var byRole = json(root, "/api/rbac/accounts?size=100&roleCode=TEACHER&branchId=" + branchA).get("items")
                .findValuesAsText("id");
        assertThat(byRole).contains(teacherA.toString()).doesNotContain(teacherB.toString(), studentA.toString());

        var byText = json(root, "/api/rbac/accounts?size=100&query=" + "list-s-a-" + suffix).get("items")
                .findValuesAsText("id");
        assertThat(byText).containsExactly(studentA.toString());

        var forced = json(adminOfA, "/api/rbac/accounts?size=100&branchId=" + branchB).get("items")
                .findValuesAsText("id");
        assertThat(forced).contains(teacherA.toString()).doesNotContain(teacherB.toString());

        assertThat(close(root, account("list-closed", AccessConstants.RoleCodes.FORMER_STAFF, branchA))).isEqualTo(200);
        assertThat(json(root, "/api/rbac/accounts?size=100&status=CLOSED&branchId=" + branchA).get("items")
                .findValuesAsText("status")).containsOnly("CLOSED");
    }

    // ---------- Học phí

    /** Ngày khai giảng là ngày thứ nhất, nên hạn là khai giảng cộng 6. Đổi khai giảng thì hạn đổi theo, trừ khi đã gia hạn. */
    @Test
    void tuitionIsDueOnTheSeventhDayFollowsTheStartDateUntilExtended() throws Exception {
        var teacher = teacher("t-due", branchA);
        var start = TODAY.plusDays(10);
        var classId = enrollingClass("DUE", branchA, teacher, start);
        var enrollmentId = enroll(account("s-due", AccessConstants.RoleCodes.STUDENT, branchA), classId);

        var issued = invoiceOf(enrollmentId);
        assertThat(issued.get("amount").decimalValue()).isEqualByComparingTo(TUITION);
        assertThat(issued.get("dueDate").asText()).isEqualTo(start.plusDays(6).toString());
        assertThat(issued.get("studentAccountId").isNull()).isFalse();

        var moved = start.plusDays(3);
        assertThat(patchClass(classId, new UpdateClassRequest(teacher, 20, 1, moved, null, MON_EVENING, null))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(invoiceOf(enrollmentId).get("dueDate").asText()).isEqualTo(moved.plusDays(6).toString());

        // Sửa lớp mà không đổi khai giảng thì không đụng tới hạn.
        var invoiceId = UUID.fromString(issued.get("id").asText());
        var extendTo = TODAY.plusDays(40);
        assertThat(send(root, "/api/billing/invoices/" + invoiceId + "/extend",
                Map.of("dueDate", extendTo, "reason", "Gia đình xin thêm thời gian")).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(patchClass(classId, new UpdateClassRequest(teacher, 20, 1, moved.plusDays(1), null, MON_EVENING,
                null)).getResponse().getStatus()).isEqualTo(200);

        var extended = invoiceOf(enrollmentId);
        assertThat(extended.get("dueDate").asText()).isEqualTo(extendTo.toString());
        assertThat(extended.get("dueExtended").asBoolean()).isTrue();
        assertThat(extended.get("extensionReason").asText()).isEqualTo("Gia đình xin thêm thời gian");
    }

    @Test
    void overdueInvoicesReachBothAdminsButOnlyTheRootAdminExtends() throws Exception {
        var classId = enrollingClass("LATE", branchA, teacher("t-late", branchA), TODAY.plusDays(10));
        var enrollmentId = enroll(account("s-late", AccessConstants.RoleCodes.STUDENT, branchA), classId);
        var invoiceId = invoiceOf(enrollmentId).get("id").asText();
        var invoice = invoices.findById(UUID.fromString(invoiceId)).orElseThrow();
        invoice.markOverdue();
        invoices.saveAndFlush(invoice);

        var adminOfB = signIn(account("admin-b", AccessConstants.RoleCodes.BRANCH_ADMIN, branchB));
        var overdue = "/api/billing/invoices?size=100&status=" + BillingConstants.InvoiceStatus.OVERDUE;
        assertThat(json(adminOfA, overdue).get("items").findValuesAsText("id")).contains(invoiceId);
        assertThat(json(root, overdue).get("items").findValuesAsText("id")).contains(invoiceId);
        assertThat(json(adminOfB, overdue).get("items").findValuesAsText("id")).doesNotContain(invoiceId);

        var body = Map.of("dueDate", TODAY.plusDays(30), "reason", "Xin gia hạn");
        assertThat(send(adminOfA, "/api/billing/invoices/" + invoiceId + "/extend", body).getResponse().getStatus())
                .isEqualTo(403);
        var extended = send(root, "/api/billing/invoices/" + invoiceId + "/extend", body);
        assertThat(extended.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(extended.getResponse().getContentAsString()).get("status").asText())
                .as("gia hạn đưa hoá đơn ra khỏi danh sách quá hạn")
                .isEqualTo(BillingConstants.InvoiceStatus.UNPAID.name());
    }

    @Test
    void withdrawingCancelsTheUnpaidInvoice() throws Exception {
        var classId = enrollingClass("WD", branchA, teacher("t-wd", branchA), TODAY.plusDays(10));
        var enrollmentId = enroll(account("s-wd", AccessConstants.RoleCodes.STUDENT, branchA), classId);

        assertThat(send(root, "/api/enrollment/enrollments/" + enrollmentId + "/withdraw", null).getResponse()
                .getStatus()).isEqualTo(200);

        assertThat(invoiceOf(enrollmentId).get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.CANCELLED.name());
    }

    // ---------- Giáo viên theo chi nhánh

    @Test
    void aClassOnlyTakesATeacherOfItsOwnBranch() throws Exception {
        var foreign = send(root, "/api/courses/classes", new CreateClassRequest(course("XB").getId(), "XB-" + suffix,
                branchA, teacher("t-foreign", branchB), 20, 1, TODAY, null, MON_EVENING));
        assertThat(foreign.getResponse().getStatus()).isEqualTo(400);
        assertThat(foreign.getResponse().getContentAsString()).contains("chi nhánh chủ quản");
    }

    @Test
    void aTeacherWithoutPayTermsCannotBeAssigned() throws Exception {
        var noPay = account("t-no-pay", AccessConstants.RoleCodes.TEACHER, branchA);

        var result = send(root, "/api/courses/classes", new CreateClassRequest(course("NP").getId(), "NP-" + suffix,
                branchA, noPay, 20, 1, TODAY, null, MON_EVENING));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /** Thay giáo viên có "từ ngày": buổi trước ngày đó vẫn tính giờ cho giáo viên cũ, kể cả khi ghi bù sau khi đã thay. */
    @Test
    void replacingATeacherCreditsSessionsByDateEvenWhenRecordedLate() throws Exception {
        var course = course("REP");
        var oldTeacher = teacher("t-old", branchA);
        var newTeacher = teacher("t-new", branchA);
        var code = "REP-K-" + suffix;
        var cls = classes.save(ClassFixtures.inProgress(course, code, branchA, oldTeacher, 20));
        var student = account("s-rep", AccessConstants.RoleCodes.STUDENT, branchA);
        var profileId = students.findProfileByAccountId(student).orElseThrow().studentProfileId();
        var enrollment = enrollments.save(new Enrollment(profileId, cls.getId(), course.getId(), branchA, student));

        assertThat(patchClass(cls.getId(), new UpdateClassRequest(newTeacher, 20, 1, cls.getStartDate(), null,
                MON_EVENING, TODAY.minusDays(5))).getResponse().getStatus()).isEqualTo(200);

        var entries = List.of(Map.of("enrollmentId", enrollment.getId(), "status", "PRESENT"));
        assertThat(send(root, "/api/attendance/sessions", Map.of("classId", cls.getId(), "sessionDate",
                TODAY.minusDays(10), "entries", entries)).getResponse().getStatus()).isEqualTo(200);
        assertThat(send(root, "/api/attendance/sessions", Map.of("classId", cls.getId(), "sessionDate",
                TODAY.minusDays(1), "entries", entries)).getResponse().getStatus()).isEqualTo(200);

        var creditedTo = jdbc.queryForList("""
                SELECT session_date, teacher_account_id FROM attendance_sessions WHERE class_id = ?
                ORDER BY session_date
                """, cls.getId());
        assertThat(creditedTo).hasSize(2);
        assertThat(creditedTo.get(0).get("teacher_account_id")).isEqualTo(oldTeacher);
        assertThat(creditedTo.get(1).get("teacher_account_id")).isEqualTo(newTeacher);

        // Giáo viên đã bàn giao vẫn xem được lớp mình từng dạy.
        assertThat(json(signIn(oldTeacher), "/api/attendance/me/taught-classes").findValuesAsText("classCode"))
                .contains(code);
    }

    // ---------- Lương theo chi nhánh

    private UUID approvedRunWith(UUID teacherA, UUID teacherB) {
        var run = new PayrollRun(2026, 1 + (int) (Math.random() * 6));
        jdbc.update("DELETE FROM payslips WHERE payroll_run_id IN (SELECT id FROM payroll_runs WHERE year = ? AND month = ?)",
                run.getYear(), run.getMonth());
        jdbc.update("DELETE FROM payroll_runs WHERE year = ? AND month = ?", run.getYear(), run.getMonth());
        payrollRuns.save(run);
        var slipA = PayslipFixtures.monthly(run, teacherA, 10_000_000);
        slipA.assignBranch(branchA);
        var slipB = PayslipFixtures.monthly(run, teacherB, 12_000_000);
        slipB.assignBranch(branchB);
        payslips.saveAll(List.of(slipA, slipB));
        run.submitForApproval();
        run.approve();
        payrollRuns.save(run);
        return run.getId();
    }

    @Test
    void aBranchAdminPaysApprovedPayslipsOfTheirBranchAndOnlyTheRootAdminUndoes() throws Exception {
        var teacherA = teacher("t-pay-a", branchA);
        var teacherB = teacher("t-pay-b", branchB);
        var runId = approvedRunWith(teacherA, teacherB);
        var path = "/api/payroll/branch-payslips?size=100";

        var seen = json(adminOfA, path + "&branchId=" + branchB).get("items");
        assertThat(seen.findValuesAsText("accountId")).contains(teacherA.toString()).doesNotContain(teacherB.toString());

        var own = slipOf(seen, teacherA);
        var foreign = slipOf(json(root, path).get("items"), teacherB);
        var payment = Map.of("paidOn", TODAY, "method", "BANK_TRANSFER");

        assertThat(send(adminOfA, payPath(foreign), payment).getResponse().getStatus()).isEqualTo(403);
        assertThat(send(adminOfA, payPath(own), Map.of("paidOn", TODAY.plusDays(1), "method", "CASH")).getResponse()
                .getStatus()).as("ngày trả ở tương lai").isEqualTo(400);
        assertThat(send(adminOfA, payPath(own), payment).getResponse().getStatus()).isEqualTo(200);
        assertThat(send(adminOfA, payPath(own), payment).getResponse().getStatus()).as("trả hai lần").isEqualTo(409);

        assertThat(json(signIn(teacherA), "/api/payroll/me/payslips").get("items").get(0).get("paidOn").asText())
                .isEqualTo(TODAY.toString());
        assertThat(json(adminOfA, path + "&paid=false").get("items").findValuesAsText("accountId"))
                .doesNotContain(teacherA.toString());

        var summaries = json(root, "/api/payroll/runs/" + runId).get("branchSummaries");
        assertThat(summaries).hasSize(2);
        assertThat(summaries.findValues("paidCount").stream().mapToInt(JsonNode::asInt).sum()).isEqualTo(1);

        var undo = "/api/payroll/branch-payslips/" + own + "/undo-payment";
        assertThat(send(adminOfA, undo, Map.of("reason", "Nhầm")).getResponse().getStatus()).isEqualTo(403);
        assertThat(send(root, undo, Map.of("reason", " ")).getResponse().getStatus()).isEqualTo(400);
        assertThat(send(root, undo, Map.of("reason", "Đánh dấu nhầm phiếu")).getResponse().getStatus()).isEqualTo(200);
        assertThat(json(signIn(teacherA), "/api/payroll/me/payslips").get("items").get(0).get("paidOn").isNull())
                .isTrue();
    }

    @Test
    void aBranchAdminCannotCreateOrApprovePayrollRuns() throws Exception {
        assertThat(send(adminOfA, "/api/payroll/runs", Map.of("year", 2026, "month", 8)).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/payroll/runs").cookie(adminOfA)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }

    private static String payPath(String payslipId) {
        return "/api/payroll/branch-payslips/" + payslipId + "/pay";
    }

    private static String slipOf(JsonNode items, UUID accountId) {
        for (var item : items) {
            if (item.get("payslip").get("accountId").asText().equals(accountId.toString())) {
                return item.get("payslip").get("id").asText();
            }
        }
        throw new AssertionError("Không thấy phiếu của " + accountId);
    }
}
