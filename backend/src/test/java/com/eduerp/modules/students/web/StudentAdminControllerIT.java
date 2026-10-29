package com.eduerp.modules.students.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.students.StudentsManagement;
import com.eduerp.modules.students.dto.CreateStudentRequest;
import com.eduerp.modules.students.dto.UpdateStudentProfileRequest;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "management.health.mail.enabled=false")
class StudentAdminControllerIT {

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
    AccessManagement access;

    @Autowired
    StudentsManagement students;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @MockBean
    JavaMailSender mailSender;

    @BeforeEach
    void mockMailSenderCreatesARealMimeMessage() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
    }

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private UUID createStudent(Cookie admin, CreateStudentRequest request) throws Exception {
        var result = mockMvc.perform(post("/api/students/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    @Test
    void createsAccountAndProfileInOneStep() throws Exception {
        var admin = signIn("student-profile-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var profileId = createStudent(admin, new CreateStudentRequest("student-one-step@eduerp.local", "Học viên A",
                null, LocalDate.of(2010, 5, 1), "0900000001", "Giới thiệu"));

        var account = accounts.findByTypedEmail("student-one-step@eduerp.local").orElseThrow();
        assertThat(account.getLastLogin()).isNull();
        assertThat(access.roleOf(account.getId())).map(AccessManagement.RoleSummary::code)
                .contains(AccessConstants.RoleCodes.STUDENT);
        assertThat(stringRedisTemplate.hasKey("invite:account:" + account.getId())).isTrue();
        assertThat(students.findProfileByAccountId(account.getId()))
                .hasValueSatisfying(profile -> assertThat(profile.studentProfileId()).isEqualTo(profileId));

        var listResult = mockMvc.perform(get("/api/students/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getContentAsString())
                .contains("student-one-step@eduerp.local", "Giới thiệu");
    }

    @Test
    void creatingAStudentRoleAccountOnTheAccountsScreenAlsoCreatesTheProfile() throws Exception {
        var admin = signIn("student-profile-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateAccountRequest("student-via-accounts@eduerp.local", "Học viên B", null,
                access.roleIdOf(AccessConstants.RoleCodes.STUDENT));

        var result = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var accountId = objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
        assertThat(students.findProfileByAccountId(accountId)).isPresent();
    }

    @Test
    void aDuplicateEmailCreatesNeitherAccountNorProfile() throws Exception {
        var admin = signIn("student-profile-admin-dup@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createStudent(admin, new CreateStudentRequest("student-dup@eduerp.local", "Học viên C", null, null, null,
                null));
        var profilesBefore = profiles.count();
        var accountsBefore = accounts.count();

        var result = mockMvc.perform(post("/api/students/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStudentRequest("student-dup@eduerp.local",
                                "Học viên trùng", null, null, null, null))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(profiles.count()).isEqualTo(profilesBefore);
        assertThat(accounts.count()).isEqualTo(accountsBefore);
    }

    @Test
    void updatesAStudentProfile() throws Exception {
        var admin = signIn("student-profile-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var profileId = createStudent(admin, new CreateStudentRequest("student-profile-2@eduerp.local",
                "Học viên D", null, null, null, null));

        var updateResult = mockMvc.perform(patch("/api/students/profiles/" + profileId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateStudentProfileRequest(LocalDate.of(2011, 1, 1), "0922222222",
                                        "Mạng xã hội", false))))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);

        var getResult = mockMvc.perform(get("/api/students/profiles/" + profileId).cookie(admin)).andReturn();
        assertThat(getResult.getResponse().getContentAsString()).contains("\"active\":false", "Mạng xã hội");
    }

    @Test
    void getsASingleStudentProfileById() throws Exception {
        var admin = signIn("student-profile-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var profileId = createStudent(admin, new CreateStudentRequest("student-profile-3@eduerp.local",
                "Học viên E", null, LocalDate.of(2012, 3, 3), "0933333333", "Giới thiệu bạn bè"));

        var result = mockMvc.perform(get("/api/students/profiles/" + profileId).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("student-profile-3@eduerp.local", "Giới thiệu bạn bè");
    }

    @Test
    void returns404ForAnUnknownStudentProfileId() throws Exception {
        var admin = signIn("student-profile-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(get("/api/students/profiles/" + UUID.randomUUID()).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("STUDENTS_PROFILE_NOT_FOUND");
    }
}
