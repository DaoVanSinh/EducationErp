package com.eduerp.modules.enrollment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
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
class EnrollmentAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String ENROLLMENTS = "/api/enrollment/enrollments";

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
    PasswordEncoder passwordEncoder;

    @Autowired
    BranchRepository branches;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    StudentProfileRepository profiles;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

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

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("enr-web-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private UUID newClassId(int maxSeats) {
        var branchId = branches.save(new Branch("ENRW-" + shortId(), "Chi nhánh", null)).getId();
        var course = courses.save(new Course("ENRW-C-" + shortId(), "Khoá", null, 24, new BigDecimal("9000000")));
        var teacherId = accounts.save(new Account("enr-web-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(new Class(course, "ENRW-K-" + shortId(), branchId, teacherId, maxSeats)).getId();
    }

    private String createEnrollment(Cookie admin, UUID studentProfileId, UUID classId) throws Exception {
        var result = mockMvc.perform(post(ENROLLMENTS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsReadsAndListsAnEnrollment() throws Exception {
        var admin = signIn("enr-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentProfileId = newStudentProfileId();
        var classId = newClassId(10);

        var enrollmentId = createEnrollment(admin, studentProfileId, classId);

        var detail = mockMvc.perform(get(ENROLLMENTS + "/" + enrollmentId).cookie(admin)).andReturn();
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(detail.getResponse().getContentAsString());
        assertThat(body.get("studentProfileId").asText()).isEqualTo(studentProfileId.toString());
        assertThat(body.get("classId").asText()).isEqualTo(classId.toString());
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("withdrawnAt").isNull()).isTrue();

        var list = mockMvc.perform(get(ENROLLMENTS).cookie(admin)
                .param("studentProfileId", studentProfileId.toString())).andReturn();
        assertThat(objectMapper.readTree(list.getResponse().getContentAsString()).get("totalItems").asInt())
                .isEqualTo(1);
    }

    /** Review Focus #1 ở tầng HTTP: lần ghi danh thứ hai phải là 409 ENROLLMENT_DUPLICATE_ACTIVE,
     * không phải 500 từ ràng buộc DB. */
    @Test
    void rejectsADuplicateActiveEnrollmentAtHttpLevel() throws Exception {
        var admin = signIn("enr-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentProfileId = newStudentProfileId();
        var classId = newClassId(10);
        createEnrollment(admin, studentProfileId, classId);

        var duplicate = mockMvc.perform(post(ENROLLMENTS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();

        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicate.getResponse().getContentAsString()).contains("ENROLLMENT_DUPLICATE_ACTIVE");
    }

    /** Review Focus #2 ở tầng HTTP với maxSeats = 2: hai ghi danh đầu 200, ghi danh thứ ba 409. */
    @Test
    void rejectsTheSeatPastCapacityAtHttpLevel() throws Exception {
        var admin = signIn("enr-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var classId = newClassId(2);
        createEnrollment(admin, newStudentProfileId(), classId);
        createEnrollment(admin, newStudentProfileId(), classId);

        var overflow = mockMvc.perform(post(ENROLLMENTS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(newStudentProfileId(), classId))))
                .andReturn();

        assertThat(overflow.getResponse().getStatus()).isEqualTo(409);
        assertThat(overflow.getResponse().getContentAsString()).contains("ENROLLMENT_CLASS_FULL");
    }

    @Test
    void withdrawsThenRefusesToWithdrawAgain() throws Exception {
        var admin = signIn("enr-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));

        var first = mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        var second = mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("ENROLLMENT_NOT_ACTIVE");
    }

    @Test
    void completesAnEnrollment() throws Exception {
        var admin = signIn("enr-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));

        var result = mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/complete").cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var detail = mockMvc.perform(get(ENROLLMENTS + "/" + enrollmentId).cookie(admin)).andReturn();
        assertThat(objectMapper.readTree(detail.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo("COMPLETED");
    }

    @Test
    void refusesEveryEndpointWithoutEnrollmentPermission() throws Exception {
        var admin = signIn("enr-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("enr-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));

        assertThat(mockMvc.perform(get(ENROLLMENTS).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(outsider)
                .with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(ENROLLMENTS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    /** Spec mục 7: màn hình "Tạo combo" gọi endpoint này với status=ACTIVE để chỉ hiện ghi danh đang
     * học; để trống thì trả về mọi trạng thái, đúng như trước. */
    @Test
    void filtersTheEnrollmentListByStatus() throws Exception {
        var admin = signIn("enr-admin-status@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));
        mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(admin).with(csrf()));

        var active = mockMvc.perform(get(ENROLLMENTS).cookie(admin)
                .param("status", EnrollmentConstants.EnrollmentStatus.ACTIVE.name())).andReturn();
        var withdrawn = mockMvc.perform(get(ENROLLMENTS).cookie(admin)
                .param("status", EnrollmentConstants.EnrollmentStatus.WITHDRAWN.name())).andReturn();

        assertThat(active.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(active.getResponse().getContentAsString()).get("items"))
                .noneSatisfy(item -> assertThat(item.get("id").asText()).isEqualTo(enrollmentId));
        assertThat(objectMapper.readTree(withdrawn.getResponse().getContentAsString()).get("items"))
                .anySatisfy(item -> assertThat(item.get("id").asText()).isEqualTo(enrollmentId));
    }
}
