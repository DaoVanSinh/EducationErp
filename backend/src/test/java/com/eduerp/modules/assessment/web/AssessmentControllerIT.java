package com.eduerp.modules.assessment.web;

import com.eduerp.modules.courses.internal.model.ClassFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.assessment.AssessmentConstants.AssignmentType;
import com.eduerp.modules.assessment.AssessmentConstants.SubmissionStatus;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
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
 * Bài tập / mini-test và mini-task qua HTTP thật: giáo viên giao bài và nhập kết quả, hệ thống tính tỷ lệ
 * hoàn thành; học viên làm mini-task không thấy đáp án trước khi nộp, được chấm tự động; học viên lớp khác
 * và giáo viên không dạy lớp bị chặn.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentControllerIT {

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

    private UUID teacherId;
    private UUID classId;
    private UUID studentAccountId;
    private UUID studentEnrollmentId;
    private UUID outsiderAccountId;

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

    private UUID newClass(Course course, UUID branchId) {
        return classes.save(ClassFixtures.inProgress(course, "AS-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private UUID enroll(UUID accountId, UUID targetClassId, Course course, UUID branchId) {
        var profileId = profiles.save(new StudentProfile(accountId, null, null, null)).getId();
        return enrollments.saveAndFlush(new Enrollment(profileId, targetClassId, course.getId(), branchId, teacherId))
                .getId();
    }

    @BeforeEach
    void seedTwoClassesWithOneStudentEach() {
        var branchId = branches.save(new Branch("AS-" + shortId(), "Chi nhánh bài tập", null)).getId();
        teacherId = newAccount("as-gv", "Cô Hoa");
        var course = courses.saveAndFlush(new Course("AS-C-" + shortId(), "Khoá bài tập", null, 24, null));
        classId = newClass(course, branchId);
        studentAccountId = newAccount("as-hv", "Học viên Lớp Này");
        studentEnrollmentId = enroll(studentAccountId, classId, course, branchId);
        outsiderAccountId = newAccount("as-hv-ngoai", "Học viên Lớp Khác");
        enroll(outsiderAccountId, newClass(course, branchId), course, branchId);
    }

    private MvcResult postJson(String path, Cookie cookie, Object body) throws Exception {
        return mockMvc.perform(post(path).cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, Cookie cookie) throws Exception {
        var result = mockMvc.perform(get(path).cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return bodyOf(result);
    }

    private UUID createAssignment(Cookie teacher) throws Exception {
        var result = postJson("/api/assessment/assignments", teacher, new AssignmentRequests.CreateAssignmentRequest(
                classId, "Bài tập Unit 1", "Làm trang 10", AssignmentType.HOMEWORK, null, new BigDecimal("10")));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(bodyOf(result).get("id").asText());
    }

    private UUID createMiniTask(Cookie teacher) throws Exception {
        var result = postJson("/api/assessment/mini-tasks", teacher, new MiniTaskRequests.CreateMiniTaskRequest(
                classId, "Ôn từ vựng", null, List.of(
                        new MiniTaskRequests.QuestionRequest("apple nghĩa là?", List.of("quả táo", "quả cam"), 0),
                        new MiniTaskRequests.QuestionRequest("cat nghĩa là?", List.of("con mèo", "con chó", "con gà"), 0))));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return UUID.fromString(bodyOf(result).get("id").asText());
    }

    private AssignmentRequests.RecordResultsRequest result(SubmissionStatus status, String score) {
        return new AssignmentRequests.RecordResultsRequest(List.of(new AssignmentRequests.ResultEntryRequest(
                studentEnrollmentId, status, score == null ? null : new BigDecimal(score), null)));
    }

    @Test
    void theTeacherTracksSubmissionsAndTheStudentSeesTheirOwnResult() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);
        var assignmentId = createAssignment(teacher);

        var recorded = postJson("/api/assessment/assignments/" + assignmentId + "/results", teacher,
                result(SubmissionStatus.SUBMITTED, "8.5"));
        assertThat(recorded.getResponse().getStatus()).isEqualTo(200);

        var board = getJson("/api/assessment/classes/" + classId + "/assignments", teacher);
        assertThat(board.get("assignments").get(0).get("submittedCount").asInt()).isEqualTo(1);
        assertThat(board.get("rows").get(0).get("completionPercent").asInt()).isEqualTo(100);

        var mine = getJson("/api/assessment/me/classes", signIn(studentAccountId, AccessConstants.RoleCodes.STUDENT));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("assignments").get(0).get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(mine.get(0).get("assignments").get(0).get("score").decimalValue()).isEqualByComparingTo("8.5");
    }

    @Test
    void aScoreAboveTheMaxOfTheAssignmentIsRejected() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);
        var assignmentId = createAssignment(teacher);

        var recorded = postJson("/api/assessment/assignments/" + assignmentId + "/results", teacher,
                result(SubmissionStatus.SUBMITTED, "11"));

        assertThat(recorded.getResponse().getStatus()).isEqualTo(400);
        assertThat(bodyOf(recorded).get("errorCode").asText()).isEqualTo("ASSESSMENT_INVALID_SCORE");
    }

    @Test
    void aTeacherWhoDoesNotTeachTheClassCannotGiveWork() throws Exception {
        var stranger = signIn(newAccount("as-gv-khac", "Thầy Khác"), AccessConstants.RoleCodes.TEACHER);

        var created = postJson("/api/assessment/assignments", stranger, new AssignmentRequests.CreateAssignmentRequest(
                classId, "Bài lạ", null, AssignmentType.MINI_TEST, null, BigDecimal.TEN));

        assertThat(created.getResponse().getStatus()).isEqualTo(403);
        assertThat(bodyOf(created).get("errorCode").asText()).isEqualTo("ASSESSMENT_NOT_ASSIGNED_TO_CLASS");
    }

    @Test
    void aQuestionWhoseAnswerIsNotAmongItsOptionsIsRejected() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);

        var created = postJson("/api/assessment/mini-tasks", teacher, new MiniTaskRequests.CreateMiniTaskRequest(
                classId, "Sai đáp án", null, List.of(
                        new MiniTaskRequests.QuestionRequest("1 + 1 = ?", List.of("1", "2"), 2))));

        assertThat(created.getResponse().getStatus()).isEqualTo(400);
        assertThat(bodyOf(created).get("errorCode").asText()).isEqualTo("ASSESSMENT_INVALID_QUESTION");
    }

    @Test
    void aStudentPractisesWithoutSeeingAnswersAndIsGradedAutomatically() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);
        var miniTaskId = createMiniTask(teacher);
        var student = signIn(studentAccountId, AccessConstants.RoleCodes.STUDENT);

        var paper = getJson("/api/assessment/me/mini-tasks/" + miniTaskId, student);
        assertThat(paper.get("questions")).hasSize(2);
        assertThat(paper.get("questions").get(0).get("correctOptionIndex").isNull()).isTrue();

        var submitted = postJson("/api/assessment/me/mini-tasks/" + miniTaskId + "/attempts", student,
                new MiniTaskRequests.SubmitAttemptRequest(java.util.Arrays.asList(0, 1)));
        assertThat(submitted.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(submitted).get("scorePercent").asInt()).isEqualTo(50);
        assertThat(bodyOf(submitted).get("results").get(1).get("correctOptionIndex").asInt()).isZero();

        var board = getJson("/api/assessment/classes/" + classId + "/mini-tasks", teacher);
        assertThat(board.get("miniTasks").get(0).get("attemptCount").asInt()).isEqualTo(1);
        assertThat(board.get("rows").get(0).get("bestScoreByMiniTaskId").get(miniTaskId.toString()).asInt())
                .isEqualTo(50);
    }

    @Test
    void aStudentOfAnotherClassCannotOpenOrSubmitTheMiniTask() throws Exception {
        var miniTaskId = createMiniTask(signIn(teacherId, AccessConstants.RoleCodes.TEACHER));
        var outsider = signIn(outsiderAccountId, AccessConstants.RoleCodes.STUDENT);

        var opened = mockMvc.perform(get("/api/assessment/me/mini-tasks/" + miniTaskId).cookie(outsider)).andReturn();
        var submitted = postJson("/api/assessment/me/mini-tasks/" + miniTaskId + "/attempts", outsider,
                new MiniTaskRequests.SubmitAttemptRequest(List.of(0, 0)));

        assertThat(opened.getResponse().getStatus()).isEqualTo(403);
        assertThat(submitted.getResponse().getStatus()).isEqualTo(403);
        assertThat(bodyOf(submitted).get("errorCode").asText()).isEqualTo("ASSESSMENT_NOT_ENROLLED_IN_CLASS");
    }

    @Test
    void aTeacherCannotSubmitPracticeAttempts() throws Exception {
        var teacher = signIn(teacherId, AccessConstants.RoleCodes.TEACHER);
        var miniTaskId = createMiniTask(teacher);

        var submitted = postJson("/api/assessment/me/mini-tasks/" + miniTaskId + "/attempts", teacher,
                new MiniTaskRequests.SubmitAttemptRequest(List.of(0, 0)));

        assertThat(submitted.getResponse().getStatus()).isEqualTo(403);
    }
}
