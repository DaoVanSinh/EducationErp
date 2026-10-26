package com.eduerp.modules.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Cổng học tập qua HTTP thật, quyền thật đọc từ DB/Redis: học viên thấy ĐÚNG lớp/điểm/hoá đơn của
 * mình (không lẫn của học viên khác), giáo viên và học viên chưa có hồ sơ được xử lý đúng.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class LearningPortalIT {

    private static final String PASSWORD = "Password123!";
    private static final String MY_CLASSES = "/api/academic/me/classes";
    private static final String MY_INVOICES = "/api/billing/me/invoices";

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
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    EnrollmentRepository enrollments;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    GradeEntryRepository gradeEntries;

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID teacherId;
    private UUID myAccountId;
    private UUID otherAccountId;
    private String myClassCode;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String emailPrefix, String fullName) {
        return accounts.save(new Account(emailPrefix + "-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), fullName, null)).getId();
    }

    private Cookie signIn(UUID accountId, String roleCode) throws Exception {
        var account = accounts.findById(accountId).orElseThrow();
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(accountId, roleCode);
        access.evictPermissionCache(accountId);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    /** Một học viên ghi danh vào lớp có hai cột điểm, đã có điểm giữa kỳ và một hoá đơn. */
    private String enroll(UUID accountId, Course course, UUID branchId, String midtermScore) {
        var code = "LP-K-" + shortId();
        var cls = new Class(course, code, branchId, teacherId, 20);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        var savedClass = classes.save(cls);
        var profileId = profiles.save(new StudentProfile(accountId, null, null, null)).getId();
        var enrollmentId = enrollments.saveAndFlush(new Enrollment(profileId, savedClass.getId(),
                course.getId(), branchId, teacherId)).getId();
        gradeEntries.save(new GradeEntry(enrollmentId, course.getGradeColumns().get(0).getId(),
                new BigDecimal(midtermScore), teacherId));
        invoices.save(new Invoice(enrollmentId, profileId, course.getId(), branchId, 1,
                new BigDecimal("1000000"), LocalDate.now().plusDays(7), teacherId));
        return code;
    }

    @BeforeEach
    void seedTwoStudentsInDifferentClasses() {
        var branchId = branches.save(new Branch("LP-" + shortId(), "Chi nhánh cổng học tập", null)).getId();
        teacherId = newAccount("lp-gv", "Cô Lan");
        var course = new Course("LP-C-" + shortId(), "Khoá cổng học tập", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);

        myAccountId = newAccount("lp-hv-me", "Học viên Tôi");
        otherAccountId = newAccount("lp-hv-other", "Học viên Khác");
        myClassCode = enroll(myAccountId, savedCourse, branchId, "8");
        enroll(otherAccountId, savedCourse, branchId, "5");
    }

    private JsonNode getJson(String path, Cookie cookie) throws Exception {
        var result = mockMvc.perform(get(path).cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void studentSeesOnlyOwnClassWithScheduleTeacherAndGrades() throws Exception {
        var me = signIn(myAccountId, AccessConstants.RoleCodes.STUDENT);

        var body = getJson(MY_CLASSES, me);

        assertThat(body).hasSize(1);
        var row = body.get(0);
        assertThat(row.get("classCode").asText()).isEqualTo(myClassCode);
        assertThat(row.get("teacherName").asText()).isEqualTo("Cô Lan");
        assertThat(row.get("schedule")).hasSize(1);
        assertThat(row.get("grades").get(0).get("score").decimalValue()).isEqualByComparingTo("8");
        assertThat(row.get("grades").get(1).get("score").isNull()).isTrue();
        assertThat(row.get("finalScore").isNull()).isTrue();
    }

    @Test
    void studentSeesOnlyOwnInvoices() throws Exception {
        var me = signIn(myAccountId, AccessConstants.RoleCodes.STUDENT);

        var body = getJson(MY_INVOICES, me);

        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("invoice").get("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(body.get(0).get("payments")).isEmpty();
    }

    @Test
    void studentWithoutAProfileGetsEmptyListsInsteadOfAnError() throws Exception {
        var newcomer = signIn(newAccount("lp-hv-new", "Học viên Mới"), AccessConstants.RoleCodes.STUDENT);

        assertThat(getJson(MY_CLASSES, newcomer)).isEmpty();
        assertThat(getJson(MY_INVOICES, newcomer)).isEmpty();
    }

    @Test
    void teacherCannotOpenTheLearningPortal() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);

        assertThat(mockMvc.perform(get(MY_CLASSES).cookie(teacher)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get(MY_INVOICES).cookie(teacher)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }
}
