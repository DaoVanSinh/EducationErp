package com.eduerp.modules.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.dto.SubmitConsultationRequest;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.StudentsManagement;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Bộ chọn cơ sở phải có tác dụng trên mọi danh sách có gắn cơ sở. Dữ liệu cố ý có người học và dạy khác cơ sở
 * chính, vì đó là trường hợp lọc đơn giản theo cơ sở chính sẽ làm sai.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BranchFilterIT {

    private static final String PASSWORD = "Password123!";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of(CoursesConstants.Schedules.CENTER_ZONE_ID));
    private static final List<WeeklyScheduleSlot> SCHEDULE = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accounts;
    @Autowired AccessManagement access;
    @Autowired CourseRepository courses;
    @Autowired BranchRepository branches;
    @Autowired StudentsManagement students;
    @Autowired TeacherProfileRepository teacherProfiles;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.eduerp.modules.courses.internal.repository.ClassRepository classes;

    private String suffix;
    private Cookie admin;
    private UUID hanoi;
    private UUID thaiBinh;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        hanoi = branches.save(new Branch("HN-" + suffix, "Hà Nội " + suffix, null)).getId();
        thaiBinh = branches.save(new Branch("TB-" + suffix, "Thái Bình " + suffix, null)).getId();
        var adminEmail = "branch-admin-" + suffix + "@eduerp.local";
        account(adminEmail, AccessConstants.RoleCodes.ADMIN, null);
        admin = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(adminEmail, PASSWORD)))
                .andReturn().getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private UUID account(String email, String roleCode, UUID homeBranchId) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, homeBranchId);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        return account.getId();
    }

    private UUID teacher(String name, UUID homeBranchId) {
        var accountId = account(name + "-" + suffix + "@eduerp.local", AccessConstants.RoleCodes.TEACHER, homeBranchId);
        com.eduerp.modules.teachers.TeacherFixtures.makeAssignable(teacherProfiles, jdbc, accountId);
        return accountId;
    }

    private UUID student(String name, UUID homeBranchId) {
        var accountId = account(name + "-" + suffix + "@eduerp.local", AccessConstants.RoleCodes.STUDENT, homeBranchId);
        return students.findProfileByAccountId(accountId).orElseThrow().studentProfileId();
    }

    private UUID course(String code) {
        return courses.save(new Course(code + "-" + suffix, "Khoá " + code, null, 24, new BigDecimal("5000000"))).getId();
    }

    /** Mỗi lớp một giáo viên của đúng chi nhánh đó: giáo viên chỉ dạy chi nhánh chủ quản và không được trùng giờ. */
    private UUID openClass(String code, UUID courseId, UUID branchId) throws Exception {
        var teacherId = teacher("t-" + code.toLowerCase(), branchId);
        var result = postJson("/api/courses/classes", new CreateClassRequest(courseId, code + "-" + suffix, branchId,
                teacherId, 10, 1, TODAY, null, SCHEDULE));
        assertThat(result).as("mở lớp %s", code).isNotBlank();
        return objectMapper.readValue(result, UUID.class);
    }

    private String enroll(UUID profileId, UUID classId) throws Exception {
        return objectMapper.readTree(postJson("/api/enrollment/enrollments",
                Map.of("studentProfileId", profileId, "classId", classId))).get("id").asText();
    }

    private String postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
    }

    /** Giá trị của {@code field} trong danh sách lọc theo một cơ sở. */
    private List<String> list(String path, UUID branchId, String field) throws Exception {
        var request = get(path).param("size", "200").cookie(admin);
        if (branchId != null) {
            request = request.param("branchId", branchId.toString());
        }
        var body = objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
        var rows = body.isArray() ? body : body.get("items");
        var values = new ArrayList<String>();
        rows.forEach(row -> values.add(row.get(field).asText()));
        return values;
    }

    @Test
    void studentsAreListedByHomeBranchOrWhereTheyStudy() throws Exception {
        var homeHanoiStudiesThaiBinh = student("s-cross", hanoi);
        var homeThaiBinh = student("s-tb", thaiBinh);
        var homeHanoi = student("s-hn", hanoi);
        var classAtThaiBinh = openClass("L-TB", course("C-S"), thaiBinh);
        enroll(homeHanoiStudiesThaiBinh, classAtThaiBinh);

        assertThat(list("/api/students/profiles", thaiBinh, "id"))
                .contains(homeHanoiStudiesThaiBinh.toString(), homeThaiBinh.toString())
                .doesNotContain(homeHanoi.toString());
        assertThat(list("/api/students/profiles", hanoi, "id"))
                .contains(homeHanoiStudiesThaiBinh.toString(), homeHanoi.toString())
                .doesNotContain(homeThaiBinh.toString());
        assertThat(list("/api/students/profiles", null, "id"))
                .as("không chọn cơ sở thì không lọc")
                .contains(homeHanoiStudiesThaiBinh.toString(), homeThaiBinh.toString(), homeHanoi.toString());
    }

    @Test
    void teachersAreListedByHomeBranchOrWhereTheyTeach() throws Exception {
        var homeHanoiTeachesThaiBinh = teacher("t-cross", hanoi);
        var homeHanoi = teacher("t-hn", hanoi);
        var homeThaiBinh = teacher("t-tb", thaiBinh);
        // Lớp cũ khác chi nhánh được giữ tới khi kết thúc: dựng thẳng vì API không còn cho mở lớp như vậy.
        classes.save(com.eduerp.modules.courses.internal.model.ClassFixtures.enrolling(
                courses.findById(course("C-T")).orElseThrow(), "L-T-" + suffix, thaiBinh, homeHanoiTeachesThaiBinh, 10));

        assertThat(list("/api/teachers/profiles", thaiBinh, "accountId"))
                .contains(homeHanoiTeachesThaiBinh.toString(), homeThaiBinh.toString())
                .doesNotContain(homeHanoi.toString());
        assertThat(list("/api/teachers/profiles", hanoi, "accountId"))
                .contains(homeHanoiTeachesThaiBinh.toString(), homeHanoi.toString())
                .doesNotContain(homeThaiBinh.toString());
    }

    @Test
    void coursesAreListedOnlyWhereTheyHaveAClassThatIsNotCancelled() throws Exception {
        var atThaiBinh = course("C-TB");
        var atHanoi = course("C-HN");
        var noClass = course("C-NONE");
        var cancelledAtThaiBinh = course("C-CANCEL");
        openClass("L-C1", atThaiBinh, thaiBinh);
        openClass("L-C2", atHanoi, hanoi);
        var cancelled = openClass("L-C3", cancelledAtThaiBinh, thaiBinh);
        mockMvc.perform(post("/api/courses/classes/" + cancelled + "/cancel").cookie(admin).with(csrf()));

        assertThat(list("/api/courses/courses", thaiBinh, "id"))
                .containsExactly(atThaiBinh.toString());
        assertThat(list("/api/courses/courses", null, "id"))
                .contains(atThaiBinh.toString(), atHanoi.toString(), noClass.toString());
    }

    @Test
    void classesEnrollmentsAndInvoicesFollowTheirOwnBranch() throws Exception {
        var courseId = course("C-E");
        var classHanoi = openClass("L-EH", courseId, hanoi);
        var classThaiBinh = openClass("L-ET", courseId, thaiBinh);
        var enrollmentHanoi = enroll(student("s-eh", null), classHanoi);
        var enrollmentThaiBinh = enroll(student("s-et", null), classThaiBinh);

        assertThat(list("/api/courses/classes", thaiBinh, "id")).containsExactly(classThaiBinh.toString());
        assertThat(list("/api/enrollment/enrollments", thaiBinh, "id")).containsExactly(enrollmentThaiBinh);
        assertThat(list("/api/billing/invoices", thaiBinh, "enrollmentId")).containsExactly(enrollmentThaiBinh);
        assertThat(list("/api/billing/invoices", null, "enrollmentId"))
                .contains(enrollmentHanoi, enrollmentThaiBinh);
    }

    @Test
    void consultationsWithoutABranchOnlyShowWhenViewingTheWholeOrganization() throws Exception {
        postJson(LandingConstants.Paths.PUBLIC_CONSULTATIONS, new SubmitConsultationRequest("Khách Thái Bình " + suffix,
                "0900000001", null, null, thaiBinh, null));
        postJson(LandingConstants.Paths.PUBLIC_CONSULTATIONS, new SubmitConsultationRequest("Khách chung " + suffix,
                "0900000002", null, null, null, null));

        assertThat(list(LandingConstants.Paths.ADMIN_CONSULTATIONS, thaiBinh, "fullName"))
                .containsExactly("Khách Thái Bình " + suffix);
        assertThat(list(LandingConstants.Paths.ADMIN_CONSULTATIONS, null, "fullName"))
                .contains("Khách Thái Bình " + suffix, "Khách chung " + suffix);
    }
}
