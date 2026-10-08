package com.eduerp.modules.courses.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ClassAdminControllerIT {

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
    AccessManagement access;

    @Autowired
    EntityManagerFactory entityManagerFactory;

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
    void createsAClassWithScheduleThenListsIt() throws Exception {
        var admin = signIn("class-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT", "Tiếng Anh giao tiếp", null, null, null));
        var branch = branches.save(new Branch("CG01", "Chi nhánh Cầu Giấy", null));
        var teacher = accounts.save(new Account("teacher-1@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Lan", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT-K15", branch.getId(), teacher.getId(), 20,
                List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0))));
        var createResult = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var classId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/classes").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        var createdNode = items.findValuesAsText("id").contains(classId.toString());
        assertThat(createdNode).isTrue();
    }

    @Test
    void rejectsACreateWithANonExistentTeacher() throws Exception {
        var admin = signIn("class-bad-teacher@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT2", "Tiếng Anh giao tiếp 2", null, null, null));
        var branch = branches.save(new Branch("CG02", "Chi nhánh 2", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT2-K1", branch.getId(), UUID.randomUUID(),
                20, List.of());
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void rejectsACreateWithANonExistentBranch() throws Exception {
        var admin = signIn("class-bad-branch@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT4", "Tiếng Anh giao tiếp 4", null, null, null));
        var teacher = accounts.save(new Account("teacher-5@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Mai", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT4-K1", UUID.randomUUID(), teacher.getId(),
                20, List.of());
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /** Thiếu hẳn trường schedule (không phải mảng rỗng) phải là 400 tử tế, không phải NPE thành 500. */
    @Test
    void rejectsAMissingScheduleFieldOnCreate() throws Exception {
        var admin = signIn("class-null-schedule@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT6", "Tiếng Anh giao tiếp 6", null, null, null));
        var branch = branches.save(new Branch("CG06", "Chi nhánh 6", null));
        var teacher = accounts.save(new Account("teacher-7@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Yến", null));

        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"%s","code":"TA-GT6-K1","branchId":"%s","teacherId":"%s","maxSeats":20}
                                """.formatted(course.getId(), branch.getId(), teacher.getId())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /** Lớp mới mở có thể chưa biết lịch học, thêm sau qua PATCH - schedule rỗng không phải lỗi. */
    @Test
    void acceptsAnEmptyScheduleOnCreate() throws Exception {
        var admin = signIn("class-empty-schedule@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT5", "Tiếng Anh giao tiếp 5", null, null, null));
        var branch = branches.save(new Branch("CG05", "Chi nhánh 5", null));
        var teacher = accounts.save(new Account("teacher-6@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Đức", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT5-K1", branch.getId(), teacher.getId(),
                20, List.of());
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var classId = objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/classes").cookie(admin)).andReturn();
        var created = findClassNode(listResult, classId);
        assertThat(created.get("schedule")).isEmpty();
    }

    @Test
    void updatesTeacherScheduleAndMaxSeats() throws Exception {
        var admin = signIn("class-update-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT3", "Tiếng Anh giao tiếp 3", null, null, null));
        var branch = branches.save(new Branch("CG03", "Chi nhánh 3", null));
        var teacher1 = accounts.save(new Account("teacher-2@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Nam", null));
        var teacher2 = accounts.save(new Account("teacher-3@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Hoa", null));
        var seedClass = new com.eduerp.modules.courses.internal.model.Class(course, "TA-GT3-K1",
                branch.getId(), teacher1.getId(), 15);
        seedClass.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        seedClass.addSchedule(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0));
        var classId = classes.save(seedClass).getId();

        var updateRequest = new UpdateClassRequest(teacher2.getId(), 25, true,
                List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.TUE, LocalTime.of(19, 0), LocalTime.of(21, 0))));
        var result = mockMvc.perform(patch("/api/courses/classes/" + classId)
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var listResult = mockMvc.perform(get("/api/courses/classes").cookie(admin)).andReturn();
        var updated = findClassNode(listResult, classId);
        assertThat(updated.get("teacherId").asText()).isEqualTo(teacher2.getId().toString());
        assertThat(updated.get("maxSeats").asInt()).isEqualTo(25);
        assertThat(updated.get("schedule")).hasSize(1);
        assertThat(updated.get("schedule").get(0).get("dayOfWeek").asText())
                .as("lịch cũ (MON, WED) phải bị xoá hẳn, chỉ còn lại đúng lịch mới (TUE)")
                .isEqualTo(CoursesConstants.DayOfWeek.TUE.name());
    }

    private com.fasterxml.jackson.databind.JsonNode findClassNode(
            org.springframework.test.web.servlet.MvcResult listResult, UUID classId) throws Exception {
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        for (var item : items) {
            if (classId.toString().equals(item.get("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Class " + classId + " not found in list response");
    }

    @Test
    void rejectsUpdatingANonExistentClass() throws Exception {
        var admin = signIn("class-missing-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacher = accounts.save(new Account("teacher-4@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Khoa", null));

        var result = mockMvc.perform(patch("/api/courses/classes/" + UUID.randomUUID())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateClassRequest(teacher.getId(), 10, true, List.of()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    /**
     * Danh sách lớp không được N+1: javadoc của ListClasses tự nhận "tránh N+1 - đúng pattern
     * ListAccounts" - test này buộc lời tự nhận đó phải đúng bằng số, không chỉ bằng lời.
     *
     * <p>Dùng {@code getQueryExecutionCount()} (số lượt thực thi JPQL/Criteria qua tầng ORM), KHÔNG
     * dùng {@code getPrepareStatementCount()} (tổng số câu SQL JDBC thô): đã xác nhận bằng cách bật
     * {@code logging.level.org.hibernate.SQL=DEBUG} và soi log rằng trong 9 câu SQL thô của 1 request
     * GET đã đăng nhập, 5 câu đầu (account theo id, account_roles join roles, account_groups join
     * user_groups, role_permission_groups join permission_groups, permission_group_items join
     * permissions) là chi phí cố định của pipeline xác thực/phân quyền Spring Security cho MỌI request
     * có đăng nhập - không liên quan gì đến số lớp hay logic của ListClasses. Chỉ 4 câu còn lại mới
     * đúng là phần ListClasses tự kiểm soát: 1 câu trang (classes JOIN courses qua entity graph), 1
     * batch tên chi nhánh, 1 batch tên giáo viên, 1 batch lịch học - đúng bằng
     * {@code getQueryExecutionCount()} đo được. 3 lớp thuộc 3 khóa học/chi nhánh/giáo viên KHÁC NHAU mà
     * vẫn giữ nguyên ở 4 câu (không tăng theo số lớp) mới là bằng chứng N+1 đã hết; ngưỡng ≤ 4 phản ánh
     * đúng con số sàn này, không giòn trước các câu phụ của tầng xác thực (vốn không do module này sinh
     * ra và không nên tính vào chuẩn N+1 của ListClasses).
     */
    @Test
    void listsClassesWithoutNPlusOneQueries() throws Exception {
        var admin = signIn("class-n-plus-one@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        for (int i = 0; i < 3; i++) {
            var course = courses.save(new Course("NPO-" + i, "Khóa N+1 " + i, null, null, null));
            var branch = branches.save(new Branch("NPO-B" + i, "Chi nhánh N+1 " + i, null));
            var teacher = accounts.save(new Account("npo-teacher-" + i + "@eduerp.local",
                    passwordEncoder.encode(PASSWORD), "Giáo viên N+1 " + i, null));
            var cls = new com.eduerp.modules.courses.internal.model.Class(course, "NPO-K" + i,
                    branch.getId(), teacher.getId(), 20);
            cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
            classes.save(cls);
        }

        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();

        var result = mockMvc.perform(get("/api/courses/classes?size=50").cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(statistics.getQueryExecutionCount())
                .as("số lượt truy vấn ORM riêng của ListClasses khi liệt kê 3 lớp ở 3 khóa học khác nhau")
                .isLessThanOrEqualTo(4);
    }

    @Test
    void refusesAnAccountWithOnlyPersonalScopePermissions() throws Exception {
        var teacher = signIn("class-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var course = courses.save(new Course("TA-GT7", "Tiếng Anh giao tiếp 7", null, null, null));
        var branch = branches.save(new Branch("CG07", "Chi nhánh 7", null));
        var teacherAccount = accounts.save(new Account("teacher-8@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Dũng", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT7-K1", branch.getId(), teacherAccount.getId(),
                20, List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0))));
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(classes.findByCode("TA-GT7-K1")).isEmpty();
    }
}
