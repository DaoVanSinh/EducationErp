package com.eduerp.modules.courses.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CourseAdminControllerIT {

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
    AccessManagement access;

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

    @Test
    void createsThenListsACourse() throws Exception {
        var admin = signIn("course-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var createResult = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24, null,
                                        List.of()))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var courseId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/courses").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        assertThat(items).anyMatch(node -> courseId.toString().equals(node.get("id").asText()));
    }

    @Test
    void updatesACourse() throws Exception {
        var admin = signIn("course-editor@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new com.eduerp.modules.courses.internal.model.Course("TOAN-9",
                "Toán lớp 9", null, null, null));

        var updateResult = mockMvc.perform(patch("/api/courses/courses/" + course.getId())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCourseRequest("Toán lớp 9 (mới)", "Cập nhật", 30, null, false,
                                        List.of()))))
                .andReturn();

        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);
        var updated = courses.findById(course.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Toán lớp 9 (mới)");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void rejectsBlankNameOnCreate() throws Exception {
        var admin = signIn("course-invalid@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("X01", "", null, null, null, List.of()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAnAccountWithOnlyPersonalScopePermissions() throws Exception {
        var teacher = signIn("course-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/courses/courses")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("XX01", "Không được phép", null, null, null,
                                        List.of()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(courses.findByCode("XX01")).isEmpty();
    }

    @Test
    void createsACourseWithGradeColumnsAndReturnsThemOnRead() throws Exception {
        var admin = signIn("course-grades-create@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var gradeColumns = List.of(
                new GradeColumnSlot(null, "Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
                new GradeColumnSlot(null, "Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1));

        var createResult = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("TA-GRADES", "Tiếng Anh có điểm", null, 24, null,
                                        gradeColumns))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var courseId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/courses").cookie(admin)).andReturn();
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        com.fasterxml.jackson.databind.JsonNode courseNode = null;
        for (var item : items) {
            if (courseId.toString().equals(item.get("id").asText())) {
                courseNode = item;
            }
        }
        assertThat(courseNode).as("vừa tạo khóa học thì phải thấy trong danh sách").isNotNull();
        assertThat(courseNode.get("gradeColumns")).hasSize(2);
        assertThat(courseNode.get("gradeColumns").get(0).get("name").asText()).isEqualTo("Giữa kỳ");
    }

    @Test
    void rejectsCreatingACourseWhoseGradeColumnWeightsDoNotSumToOneHundred() throws Exception {
        var admin = signIn("course-grades-invalid@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var brokenColumns = List.of(
                new GradeColumnSlot(null, "Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
                new GradeColumnSlot(null, "Cuối kỳ", new BigDecimal("50.00"), new BigDecimal("10.00"), 1));

        var result = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("TA-BADGRADES", "Tiếng Anh điểm sai", null, 24, null,
                                        brokenColumns))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(courses.findByCode("TA-BADGRADES")).isEmpty();
    }

    /** Review Focus #6 end-to-end: tổng trọng số sai phải chặn cả khi SỬA, không chỉ lúc tạo mới. */
    @Test
    void rejectsAPatchWhoseGradeColumnWeightsDoNotSumToOneHundred() throws Exception {
        var admin = signIn("course-grades-patch-invalid@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = new com.eduerp.modules.courses.internal.model.Course("TA-PATCHBAD",
                "Tiếng Anh sửa điểm sai", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var saved = courses.saveAndFlush(course);
        var midtermId = saved.getGradeColumns().get(0).getId();
        var finalId = saved.getGradeColumns().get(1).getId();

        var result = mockMvc.perform(patch("/api/courses/courses/" + saved.getId())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCourseRequest(
                                "Tiếng Anh sửa điểm sai", null, 24, null, true, List.of(
                                        new GradeColumnSlot(midtermId, "Giữa kỳ", new BigDecimal("40.00"),
                                                new BigDecimal("10.00"), 0),
                                        new GradeColumnSlot(finalId, "Cuối kỳ", new BigDecimal("50.00"),
                                                new BigDecimal("10.00"), 1))))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("COURSES_GRADE_COLUMN_WEIGHTS_INVALID");

        var listResult = mockMvc.perform(get("/api/courses/courses").cookie(admin)).andReturn();
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        com.fasterxml.jackson.databind.JsonNode courseNode = null;
        for (var item : items) {
            if (saved.getId().toString().equals(item.get("id").asText())) {
                courseNode = item;
            }
        }
        assertThat(courseNode).isNotNull();
        var names = new java.util.ArrayList<String>();
        courseNode.get("gradeColumns").forEach(node -> names.add(node.get("name").asText()));
        assertThat(names).as("PATCH bị chặn thì cấu hình cũ không được chạm tới")
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }
}
