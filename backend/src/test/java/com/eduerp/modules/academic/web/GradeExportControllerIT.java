package com.eduerp.modules.academic.web;

import com.eduerp.modules.courses.internal.model.ClassFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.academic.AcademicConstants;
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
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Review Focus #7 end-to-end: con số trong file {@code .xlsx} tải về phải KHỚP con số endpoint JSON
 * trả ra, đọc bằng POI từ chính byte của HTTP response. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GradeExportControllerIT {

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
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @BeforeEach
    void seedAFullyGradedClass() {
        var branchId = branches.save(new Branch("GX-" + shortId(), "Chi nhánh xuất Excel", null)).getId();
        assignedTeacherId = newAccount("gx-gv", "Cô Lan");
        var course = new Course("GX-C-" + shortId(), "Khoá xuất Excel", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        midtermColumnId = savedCourse.getGradeColumns().get(0).getId();
        finalColumnId = savedCourse.getGradeColumns().get(1).getId();
        var cls = ClassFixtures.inProgress(savedCourse, "GX-K-" + shortId(), branchId, assignedTeacherId, 20);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        classId = classes.save(cls).getId();
        var studentProfileId = profiles.save(
                new StudentProfile(newAccount("gx-hv", "Nguyễn Văn A"), null, null, null)).getId();
        enrollmentId = enrollments.saveAndFlush(new Enrollment(studentProfileId, classId,
                savedCourse.getId(), branchId, assignedTeacherId)).getId();
    }

    private void recordGrade(Cookie cookie, UUID columnId, String score) throws Exception {
        var status = mockMvc.perform(post("/api/academic/grades")
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordGradeRequest(classId,
                                enrollmentId, columnId, new BigDecimal(score)))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    private static double numericCell(byte[] content, int rowIndex, int cellIndex) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return workbook.getSheetAt(0).getRow(rowIndex).getCell(cellIndex).getNumericCellValue();
        }
    }

    /**
     * Review Focus #7: điểm tổng kết trong file Excel phải bằng ĐÚNG số JSON trả về. Nếu ai đó thêm
     * một đường tính riêng cho Excel thì hai con số này tách nhau và test đỏ.
     */
    @Test
    void exportedGradebookNumbersMatchTheJsonEndpoint() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);
        recordGrade(teacher, midtermColumnId, "8.00");
        recordGrade(teacher, finalColumnId, "9.00");

        var json = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(teacher)).andReturn();
        var jsonFinalScore = objectMapper.readTree(json.getResponse().getContentAsString())
                .get("rows").get(0).get("finalScore").asDouble();

        var export = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook/export")
                .cookie(teacher)).andReturn();

        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        assertThat(export.getResponse().getContentType())
                .isEqualTo(AcademicConstants.Excel.CONTENT_TYPE);
        assertThat(export.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(AcademicConstants.Excel.GRADEBOOK_FILE_NAME);
        var content = export.getResponse().getContentAsByteArray();
        assertThat(numericCell(content, 1, 3))
                .as("ô Điểm tổng kết của dòng đầu, khớp đúng số JSON")
                .isEqualTo(jsonFinalScore);
        assertThat(jsonFinalScore).isEqualTo(86.0);
    }

    /** Review Focus #7, bảng thứ hai: số buổi đã học và % trong Excel khớp JSON. */
    @Test
    void exportedProgressNumbersMatchTheJsonEndpoint() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        var json = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(teacher)).andReturn();
        var row = objectMapper.readTree(json.getResponse().getContentAsString()).get("rows").get(0);
        var jsonElapsed = row.get("elapsedSessions").asDouble();
        var jsonPercent = row.get("progressPercent").asDouble();

        var export = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress/export")
                .cookie(teacher)).andReturn();

        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        assertThat(export.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(AcademicConstants.Excel.PROGRESS_FILE_NAME);
        var content = export.getResponse().getContentAsByteArray();
        assertThat(numericCell(content, 1, 3)).isEqualTo(jsonElapsed);
        assertThat(numericCell(content, 1, 5)).isEqualTo(jsonPercent);
    }

    /** Spec mục 7 + Review Focus #4: hai endpoint tải file cũng bị chặn 403 với giáo viên sai lớp -
     * tải Excel không phải cửa sau. */
    @Test
    void refusesBothExportsForATeacherNotAssignedToTheClass() throws Exception {
        var outsider = signIn(newAccount("gx-gv-outsider", "Thầy Nam"),
                AccessConstants.RoleCodes.TEACHER);

        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook/export")
                .cookie(outsider)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/progress/export")
                .cookie(outsider)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    /** Review Focus #5: admin tải được Excel của lớp mình không dạy. */
    @Test
    void allowsAnAdminToExportAClassTheyDoNotTeach() throws Exception {
        var admin = signIn(newAccount("gx-admin", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook/export")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/progress/export")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
