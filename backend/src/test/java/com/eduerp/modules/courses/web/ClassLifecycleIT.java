package com.eduerp.modules.courses.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.courses.usecase.AutoStartClasses;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.StudentsManagement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * "Khi nào lớp đủ học viên, khi nào bắt đầu học" - vòng đời lớp V31 chạy thật qua HTTP: mở lớp → ghi danh →
 * khai giảng khi đủ sĩ số tối thiểu → điểm danh → kết thúc; hoặc huỷ khi không đủ người.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ClassLifecycleIT {

    private static final String PASSWORD = "Password123!";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of(CoursesConstants.Schedules.CENTER_ZONE_ID));
    private static final List<WeeklyScheduleSlot> MON_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)));

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
    CourseRepository courses;

    @Autowired
    BranchRepository branches;

    @Autowired
    CoursesManagement coursesManagement;

    @Autowired
    EnrollmentManagement enrollmentManagement;

    @Autowired
    StudentsManagement students;

    @Autowired
    AutoStartClasses autoStartClasses;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID account(String email, String roleCode) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        return account.getId();
    }

    private Cookie signIn(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn().getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private Cookie admin(String suffix) throws Exception {
        var email = "lc-admin-" + suffix + "@eduerp.local";
        account(email, AccessConstants.RoleCodes.ADMIN);
        return signIn(email);
    }

    private UUID openClass(Cookie admin, String code, int minSeats, int maxSeats, LocalDate startDate)
            throws Exception {
        var course = courses.save(new Course(code + "-C", "Khoá " + code, null, null, null));
        var branch = branches.save(new Branch(code + "-B", "Chi nhánh " + code, null));
        var teacher = account(code.toLowerCase() + "-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var result = postJson(admin, "/api/courses/classes", new CreateClassRequest(course.getId(), code,
                branch.getId(), teacher, maxSeats, minSeats, startDate, null, MON_EVENING));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    private MvcResult enroll(Cookie admin, UUID classId, String studentEmail) throws Exception {
        var studentAccount = account(studentEmail, AccessConstants.RoleCodes.STUDENT);
        var profileId = students.findProfileByAccountId(studentAccount).orElseThrow().studentProfileId();
        return postJson(admin, "/api/enrollment/enrollments",
                java.util.Map.of("studentProfileId", profileId, "classId", classId));
    }

    private MvcResult postJson(Cookie cookie, String path, Object body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private MvcResult action(Cookie cookie, UUID classId, String action) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/courses/classes/" + classId + "/" + action).cookie(cookie).with(csrf())).andReturn();
    }

    private CoursesConstants.ClassStatus statusOf(UUID classId) {
        return coursesManagement.getClassInfo(classId).orElseThrow().status();
    }

    @Test
    void aNewClassIsEnrollingAndStartsOnlyOnceItHasEnoughStudents() throws Exception {
        var admin = admin("start");
        var classId = openClass(admin, "LC-START", 2, 20, TODAY);
        assertThat(statusOf(classId)).isEqualTo(CoursesConstants.ClassStatus.ENROLLING);
        assertThat(enroll(admin, classId, "lc-start-s1@eduerp.local").getResponse().getStatus()).isEqualTo(200);

        var tooFew = action(admin, classId, "start");
        assertThat(tooFew.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooFew.getResponse().getContentAsString()).contains("COURSES_CLASS_NOT_ENOUGH_STUDENTS");

        enroll(admin, classId, "lc-start-s2@eduerp.local");
        assertThat(action(admin, classId, "start").getResponse().getStatus()).isEqualTo(200);
        assertThat(statusOf(classId)).isEqualTo(CoursesConstants.ClassStatus.IN_PROGRESS);

        var list = mockMvc.perform(get("/api/courses/classes").cookie(admin)).andReturn();
        assertThat(list.getResponse().getContentAsString()).contains("\"activeEnrollments\":2");
    }

    @Test
    void attendanceIsOnlyRecordedOnceTheClassHasStarted() throws Exception {
        var admin = admin("att");
        var classId = openClass(admin, "LC-ATT", 1, 20, TODAY);
        var enrollmentId = objectMapper.readTree(enroll(admin, classId, "lc-att-s1@eduerp.local")
                .getResponse().getContentAsString()).get("id").asText();
        var attendance = java.util.Map.of("classId", classId, "sessionDate", TODAY.toString(),
                "entries", List.of(java.util.Map.of("enrollmentId", enrollmentId, "status", "PRESENT")));

        var beforeStart = postJson(admin, "/api/attendance/sessions", attendance);
        assertThat(beforeStart.getResponse().getStatus()).isEqualTo(409);
        assertThat(beforeStart.getResponse().getContentAsString()).contains("ATTENDANCE_CLASS_NOT_IN_PROGRESS");

        action(admin, classId, "start");
        assertThat(postJson(admin, "/api/attendance/sessions", attendance).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void completingAClassCompletesItsEnrollmentsAndClosesItToNewOnes() throws Exception {
        var admin = admin("done");
        var classId = openClass(admin, "LC-DONE", 1, 20, TODAY);
        enroll(admin, classId, "lc-done-s1@eduerp.local");
        action(admin, classId, "start");

        assertThat(action(admin, classId, "complete").getResponse().getStatus()).isEqualTo(200);

        assertThat(statusOf(classId)).isEqualTo(CoursesConstants.ClassStatus.COMPLETED);
        assertThat(enrollmentManagement.listEnrollmentsByClass(classId))
                .allSatisfy(entry -> assertThat(entry.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.COMPLETED));
        var late = enroll(admin, classId, "lc-done-late@eduerp.local");
        assertThat(late.getResponse().getStatus()).isEqualTo(409);
        assertThat(late.getResponse().getContentAsString()).contains("ENROLLMENT_CLASS_NOT_ACTIVE");
    }

    @Test
    void cancellingAnUnderfilledClassWithdrawsItsEnrollments() throws Exception {
        var admin = admin("cancel");
        var classId = openClass(admin, "LC-CXL", 10, 20, TODAY);
        enroll(admin, classId, "lc-cxl-s1@eduerp.local");

        assertThat(action(admin, classId, "cancel").getResponse().getStatus()).isEqualTo(200);

        assertThat(statusOf(classId)).isEqualTo(CoursesConstants.ClassStatus.CANCELLED);
        assertThat(enrollmentManagement.listEnrollmentsByClass(classId))
                .allSatisfy(entry -> assertThat(entry.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.WITHDRAWN));
        assertThat(action(admin, classId, "start").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void theDailyJobStartsEveryClassThatIsDueAndFullEnough() throws Exception {
        var admin = admin("auto");
        var ready = openClass(admin, "LC-AUTO-OK", 1, 20, TODAY);
        enroll(admin, ready, "lc-auto-s1@eduerp.local");
        var underfilled = openClass(admin, "LC-AUTO-LOW", 3, 20, TODAY);
        enroll(admin, underfilled, "lc-auto-s2@eduerp.local");
        var future = openClass(admin, "LC-AUTO-LATER", 1, 20, TODAY.plusDays(7));
        enroll(admin, future, "lc-auto-s3@eduerp.local");

        autoStartClasses.execute();

        assertThat(statusOf(ready)).isEqualTo(CoursesConstants.ClassStatus.IN_PROGRESS);
        assertThat(statusOf(underfilled)).as("chưa đủ sĩ số - giữ nguyên chờ admin quyết")
                .isEqualTo(CoursesConstants.ClassStatus.ENROLLING);
        assertThat(statusOf(future)).as("chưa tới ngày khai giảng").isEqualTo(CoursesConstants.ClassStatus.ENROLLING);
    }

    @Test
    void rejectsAPastStartDateAndMaxSeatsBelowTheCurrentHeadcount() throws Exception {
        var admin = admin("rules");
        var course = courses.save(new Course("LC-RULE-MANUAL", "Khoá luật", null, null, null));
        var branch = branches.save(new Branch("LC-RULE-MB", "Chi nhánh luật", null));
        var teacher = account("lc-rule-manual-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var past = postJson(admin, "/api/courses/classes", new CreateClassRequest(course.getId(), "LC-RULE-PAST",
                branch.getId(), teacher, 20, 1, TODAY.minusDays(1), null, MON_EVENING));
        assertThat(past.getResponse().getStatus()).isEqualTo(400);

        var classId = openClass(admin, "LC-RULE", 1, 20, TODAY);
        var info = coursesManagement.getClassInfo(classId).orElseThrow();
        enroll(admin, classId, "lc-rule-s1@eduerp.local");
        enroll(admin, classId, "lc-rule-s2@eduerp.local");

        var shrink = mockMvc.perform(patch("/api/courses/classes/" + classId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateClassRequest(info.teacherId(), 1, 1,
                                TODAY, null, MON_EVENING))))
                .andReturn();
        assertThat(shrink.getResponse().getStatus()).isEqualTo(409);
        assertThat(shrink.getResponse().getContentAsString()).contains("COURSES_CLASS_SEATS_BELOW_ENROLLED");
    }
}
