package com.eduerp.modules.courses.web;

import com.eduerp.modules.courses.internal.model.ClassFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
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
 * Final review Critical #1: {@code course_grade_columns} bị {@code grade_entries} tham chiếu bằng
 * khoá ngoại (V24), nhưng {@code UpdateCourse} từng xoá-và-tạo-lại TOÀN BỘ danh sách mỗi lần PATCH,
 * kể cả khi chỉ sửa tên - {@code orphanRemoval} xoá đúng dòng đang bị {@code grade_entries} tham
 * chiếu và vỡ FK thành một 500 trống. Hai test dưới đây chứng minh bản vá: (a) sửa tên một cột đã
 * có điểm giữ nguyên {@code id}, điểm đã nhập còn nguyên; (b) bỏ hẳn một cột đã có điểm trả về lỗi
 * nghiệp vụ 409 có errorCode, không phải 500.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CourseGradeColumnIntegrityIT {

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
    GradeEntryRepository gradeEntries;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Cookie signInAdmin() throws Exception {
        var account = accounts.save(new Account("cgc-admin-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Quản trị", null));
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), AccessConstants.RoleCodes.ADMIN);
        access.evictPermissionCache(account.getId());
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private record Fixture(UUID courseId, UUID enrollmentId, UUID midtermColumnId, UUID finalColumnId) {
    }

    private Fixture seedCourseWithOneGradedColumn() {
        var course = new Course("CGC-" + shortId(), "Khoá kiểm tra FK", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        var midtermId = savedCourse.getGradeColumns().get(0).getId();
        var finalId = savedCourse.getGradeColumns().get(1).getId();

        var branchId = branches.save(new Branch("CGC-B-" + shortId(), "Chi nhánh kiểm tra FK", null)).getId();
        var teacherId = accounts.save(
                new Account("cgc-gv-" + shortId() + "@eduerp.local", "hash", "GV", null)).getId();
        var classId = classes.save(
                ClassFixtures.inProgress(savedCourse, "CGC-K-" + shortId(), branchId, teacherId, 20)).getId();
        var studentProfileId = profiles.save(new StudentProfile(
                accounts.save(new Account("cgc-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId(),
                null, null, null)).getId();
        var enrollmentId = enrollments.saveAndFlush(
                new Enrollment(studentProfileId, classId, savedCourse.getId(), branchId, teacherId)).getId();
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermId, new BigDecimal("8.00"), teacherId));

        return new Fixture(savedCourse.getId(), enrollmentId, midtermId, finalId);
    }

    @Test
    void preservesAnExistingGradeColumnsIdWhenOnlyItsNameChangesSoTheGradeSurvives() throws Exception {
        var fixture = seedCourseWithOneGradedColumn();
        var admin = signInAdmin();

        var result = mockMvc.perform(patch("/api/courses/courses/" + fixture.courseId())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCourseRequest(
                                "Khoá kiểm tra FK (sửa)", null, 24, null, true, List.of(
                                        new GradeColumnSlot(fixture.midtermColumnId(), "Giữa kỳ (sửa tên)",
                                                new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
                                        new GradeColumnSlot(fixture.finalColumnId(), "Cuối kỳ",
                                                new BigDecimal("60.00"), new BigDecimal("10.00"), 1))))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/courses/courses").cookie(admin)).andReturn();
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        com.fasterxml.jackson.databind.JsonNode courseNode = null;
        for (var item : items) {
            if (fixture.courseId().toString().equals(item.get("id").asText())) {
                courseNode = item;
            }
        }
        assertThat(courseNode).isNotNull();
        var ids = new java.util.ArrayList<String>();
        var names = new java.util.ArrayList<String>();
        courseNode.get("gradeColumns").forEach(node -> {
            ids.add(node.get("id").asText());
            names.add(node.get("name").asText());
        });
        assertThat(ids).as("id cột cũ phải được giữ nguyên, không phải xoá-rồi-tạo-lại")
                .containsExactlyInAnyOrder(fixture.midtermColumnId().toString(), fixture.finalColumnId().toString());
        assertThat(names).contains("Giữa kỳ (sửa tên)");
        assertThat(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(fixture.enrollmentId(),
                fixture.midtermColumnId()))
                .as("điểm đã nhập cho cột đó phải còn nguyên")
                .isPresent();
    }

    @Test
    void rejectsRemovingAGradeColumnThatAlreadyHasAGradeEnteredForItWithABusinessError() throws Exception {
        var fixture = seedCourseWithOneGradedColumn();
        var admin = signInAdmin();

        var result = mockMvc.perform(patch("/api/courses/courses/" + fixture.courseId())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCourseRequest(
                                "Khoá kiểm tra FK", null, 24, null, true, List.of(
                                        new GradeColumnSlot(null, "Cột mới thay thế hoàn toàn",
                                                new BigDecimal("100.00"), new BigDecimal("10.00"), 0))))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("COURSES_GRADE_COLUMN_IN_USE");
        assertThat(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(fixture.enrollmentId(),
                fixture.midtermColumnId()))
                .as("lỗi phải chặn TRƯỚC khi xoá, điểm đã nhập còn nguyên")
                .isPresent();
    }
}
