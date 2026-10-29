package com.eduerp.modules.attendance.web;

import com.eduerp.modules.courses.internal.model.ClassFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.attendance.AttendanceConstants.AttendanceStatus;
import com.eduerp.modules.attendance.dto.AttendanceEntryRequest;
import com.eduerp.modules.attendance.dto.RecordAttendanceRequest;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Điểm danh qua HTTP thật, quyền thật đọc từ DB/Redis: giáo viên được phân công ghi và xem được, giáo viên
 * khác bị chặn, admin thao tác mọi lớp, học viên chỉ xem điểm danh của chính mình.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AttendanceControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String SESSIONS = "/api/attendance/sessions";
    private static final String MY_ATTENDANCE = "/api/attendance/me/classes";

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

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private UUID teacherId;
    private UUID classId;
    private UUID studentAccountId;
    private UUID studentEnrollmentId;
    private UUID classmateEnrollmentId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String prefix, String fullName) {
        return accounts.save(new Account(prefix + "-" + shortId() + "@eduerp.local",
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

    private UUID enroll(UUID accountId, Course course, UUID branchId) {
        var profileId = profiles.save(new StudentProfile(accountId, null, null, null)).getId();
        return enrollments.saveAndFlush(new Enrollment(profileId, classId, course.getId(), branchId, teacherId)).getId();
    }

    @BeforeEach
    void seedAClassWithTwoStudents() {
        var branchId = branches.save(new Branch("AT-" + shortId(), "Chi nhánh điểm danh", null)).getId();
        teacherId = newAccount("at-gv", "Thầy Minh");
        var course = courses.saveAndFlush(new Course("AT-C-" + shortId(), "Khoá điểm danh", null, 24, null));
        var cls = ClassFixtures.inProgress(course, "AT-K-" + shortId(), branchId, teacherId, 20);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        classId = classes.save(cls).getId();
        studentAccountId = newAccount("at-hv", "Học viên Chăm");
        studentEnrollmentId = enroll(studentAccountId, course, branchId);
        classmateEnrollmentId = enroll(newAccount("at-hv2", "Học viên Bạn"), course, branchId);
    }

    private MvcResult record(Cookie cookie, LocalDate date, AttendanceEntryRequest... entries) throws Exception {
        var body = new RecordAttendanceRequest(classId, date, "Buổi học", List.of(entries));
        return mockMvc.perform(post(SESSIONS).cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode getJson(String path, Cookie cookie) throws Exception {
        var result = mockMvc.perform(get(path).cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String errorCodeOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("errorCode").asText();
    }

    @Test
    void theAssignedTeacherRecordsAndReadsTheAttendanceSheet() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);

        var result = record(teacher, today,
                new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.PRESENT, null),
                new AttendanceEntryRequest(classmateEnrollmentId, AttendanceStatus.ABSENT, "Ốm"));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        var sheet = getJson("/api/attendance/classes/" + classId, teacher);
        assertThat(sheet.get("sessions")).hasSize(1);
        assertThat(sheet.get("sessions").get(0).get("summary").get("absentCount").asInt()).isEqualTo(1);
        assertThat(sheet.get("rows")).hasSize(2);
    }

    @Test
    void recordingTheSameDayAgainEditsThatSession() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);
        record(teacher, today, new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.ABSENT, null));
        record(teacher, today, new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.LATE, null));

        var sheet = getJson("/api/attendance/classes/" + classId, teacher);
        assertThat(sheet.get("sessions")).hasSize(1);
        assertThat(sheet.get("sessions").get(0).get("summary").get("lateCount").asInt()).isEqualTo(1);
    }

    @Test
    void aTeacherWhoDoesNotTeachTheClassIsRefused() throws Exception {
        var stranger = signIn(newAccount("at-gv-khac", "Cô Khác"), AccessConstants.RoleCodes.TEACHER);

        var result = record(stranger, today,
                new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.PRESENT, null));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCodeOf(result)).isEqualTo("ATTENDANCE_NOT_ASSIGNED_TO_CLASS");
    }

    @Test
    void anAdminCanRecordForAnyClass() throws Exception {
        var admin = signIn(newAccount("at-admin", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        var result = record(admin, today,
                new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.EXCUSED, null));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aFutureDateIsRejected() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);

        var result = record(teacher, today.plusDays(2),
                new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.PRESENT, null));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(errorCodeOf(result)).isEqualTo("ATTENDANCE_FUTURE_SESSION_DATE");
    }

    @Test
    void aStudentSeesOnlyTheirOwnAttendanceAndCannotRecordAny() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);
        record(teacher, today.minusDays(7),
                new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.PRESENT, null),
                new AttendanceEntryRequest(classmateEnrollmentId, AttendanceStatus.ABSENT, null));
        record(teacher, today, new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.ABSENT, null));
        var student = signIn(studentAccountId, AccessConstants.RoleCodes.STUDENT);

        var mine = getJson(MY_ATTENDANCE, student);

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("sessions")).hasSize(2);
        assertThat(mine.get(0).get("summary").get("attendancePercent").asInt()).isEqualTo(50);
        var attempt = record(student, today,
                new AttendanceEntryRequest(studentEnrollmentId, AttendanceStatus.PRESENT, null));
        assertThat(attempt.getResponse().getStatus()).isEqualTo(403);
    }
}
