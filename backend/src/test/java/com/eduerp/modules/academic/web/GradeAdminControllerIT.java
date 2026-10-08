package com.eduerp.modules.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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
 * Review Focus #4 + #5 end-to-end qua HTTP thật, với quyền thật đọc từ Redis: đây là chỗ duy nhất
 * chứng minh rằng {@code @PreAuthorize} KHÔNG chặn giáo viên scope PERSONAL (cửa mở) nhưng guard
 * trong usecase VẪN chặn giáo viên sai lớp (403). Mirror {@code ClassAdminControllerIT} về cách
 * đăng nhập bằng cookie.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GradeAdminControllerIT {

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID assignedTeacherId;
    private UUID classId;
    private UUID enrollmentId;
    private UUID midtermColumnId;
    private UUID finalColumnId;

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
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @BeforeEach
    void seedAClassWithOneStudentAndTwoGradeColumns() {
        var branchId = branches.save(new Branch("GB-" + shortId(), "Chi nhánh sổ điểm", null)).getId();
        assignedTeacherId = newAccount("gb-gv", "Cô Lan");
        var course = new Course("GB-C-" + shortId(), "Khoá có sổ điểm", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        midtermColumnId = savedCourse.getGradeColumns().get(0).getId();
        finalColumnId = savedCourse.getGradeColumns().get(1).getId();
        var cls = new Class(savedCourse, "GB-K-" + shortId(), branchId, assignedTeacherId, 20);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        classId = classes.save(cls).getId();
        var studentProfileId = profiles.save(
                new StudentProfile(newAccount("gb-hv", "Nguyễn Văn A"), null, null, null)).getId();
        enrollmentId = enrollments.saveAndFlush(new Enrollment(studentProfileId, classId,
                savedCourse.getId(), branchId, assignedTeacherId)).getId();
    }

    private int recordGrade(Cookie cookie, UUID columnId, String score) throws Exception {
        return mockMvc.perform(post("/api/academic/grades")
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordGradeRequest(classId,
                                enrollmentId, columnId, new BigDecimal(score)))))
                .andReturn().getResponse().getStatus();
    }

    /** Review Focus #4, chiều hợp lệ: giáo viên ĐÚNG lớp, chỉ có GRADE:*:PERSONAL, nhập điểm được và
     * đọc được bảng điểm. */
    @Test
    void allowsTheAssignedTeacherToRecordAndReadGrades() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        assertThat(recordGrade(teacher, midtermColumnId, "8.00")).isEqualTo(200);
        assertThat(recordGrade(teacher, finalColumnId, "9.00")).isEqualTo(200);

        var result = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(teacher)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("columns")).hasSize(2);
        assertThat(body.get("rows")).hasSize(1);
        assertThat(body.get("rows").get(0).get("studentName").asText()).isEqualTo("Nguyễn Văn A");
        assertThat(body.get("rows").get(0).get("finalScore").asDouble())
                .as("8/10*40 + 9/10*60 = 86")
                .isEqualTo(86.0);
    }

    /**
     * Review Focus #4, chiều bị chặn và là khẳng định quan trọng nhất của cả Phân hệ 4: giáo viên
     * này có ĐÚNG cùng bộ quyền {@code GRADE:READ/UPDATE:PERSONAL} như người trên (cùng role
     * TEACHER), nên {@code @PreAuthorize} cho qua - chỉ guard trong usecase chặn lại.
     */
    @Test
    void refusesATeacherWhoIsNotAssignedToTheClass() throws Exception {
        var outsider = signIn(newAccount("gb-gv-outsider", "Thầy Nam"),
                AccessConstants.RoleCodes.TEACHER);

        assertThat(recordGrade(outsider, midtermColumnId, "8.00")).isEqualTo(403);

        var gradebook = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(outsider)).andReturn();
        assertThat(gradebook.getResponse().getStatus()).isEqualTo(403);
        assertThat(gradebook.getResponse().getContentAsString())
                .contains("ACADEMIC_NOT_ASSIGNED_TO_CLASS");

        var progress = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(outsider)).andReturn();
        assertThat(progress.getResponse().getStatus()).isEqualTo(403);
    }

    /** Review Focus #5: admin scope ORGANIZATION thao tác được trên lớp mình không dạy. */
    @Test
    void allowsAnAdminOnAClassTheyDoNotTeach() throws Exception {
        var admin = signIn(newAccount("gb-admin", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        assertThat(recordGrade(admin, midtermColumnId, "7.50")).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    /** Global Constraint: học viên không có quyền GRADE nào nên bị chặn ngay ở cửa
     * {@code @PreAuthorize} (403), không cần tới guard. */
    @Test
    void refusesAStudentEntirely() throws Exception {
        var student = signIn(newAccount("gb-student", "Học viên tò mò"),
                AccessConstants.RoleCodes.STUDENT);

        assertThat(recordGrade(student, midtermColumnId, "10.00")).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(student)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    /** Review Focus #3 qua HTTP: thiếu cột cuối kỳ thì finalScore là null trong JSON, không phải 32. */
    @Test
    void returnsANullFinalScoreWhenOneColumnHasNoScore() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);
        assertThat(recordGrade(teacher, midtermColumnId, "8.00")).isEqualTo(200);

        var result = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(teacher)).andReturn();

        var row = objectMapper.readTree(result.getResponse().getContentAsString()).get("rows").get(0);
        assertThat(row.get("finalScore").isNull()).isTrue();
        assertThat(row.get("scoresByColumnId").size()).isEqualTo(1);
    }

    /** Review Focus #10 qua HTTP: điểm vượt thang của cột là 400 có errorCode, không phải 500. */
    @Test
    void rejectsAScoreAboveTheColumnMaxWithAProblemDetail() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/academic/grades")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordGradeRequest(classId,
                                enrollmentId, midtermColumnId, new BigDecimal("10.50")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("ACADEMIC_INVALID_SCORE");
    }

    /** Lớp không tồn tại là 404, phân biệt rõ với 403 "không phải lớp của anh". */
    @Test
    void reportsAnUnknownClassAsNotFoundForAnAdmin() throws Exception {
        var admin = signIn(newAccount("gb-admin-404", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(get("/api/academic/classes/" + UUID.randomUUID() + "/gradebook")
                .cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("ACADEMIC_CLASS_NOT_FOUND");
    }

    /** Review Focus #1 + #8 qua HTTP: bảng tiến trình trả số thật và nói rõ tổng số buổi chuẩn. */
    @Test
    void returnsTheProgressTableWithElapsedSessionsAndStandardCount() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(teacher)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("standardSessionCount").asInt()).isEqualTo(24);
        assertThat(body.get("rows")).hasSize(1);
        var row = body.get("rows").get(0);
        assertThat(row.get("studentName").asText()).isEqualTo("Nguyễn Văn A");
        assertThat(row.get("elapsedSessions").isInt()).isTrue();
        assertThat(row.get("progressPercent").asInt()).isBetween(0, 100);
    }
}
