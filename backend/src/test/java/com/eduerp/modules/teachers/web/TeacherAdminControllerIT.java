package com.eduerp.modules.teachers.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.teachers.TeachersManagement;
import com.eduerp.modules.teachers.dto.CreateTeacherRequest;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class TeacherAdminControllerIT {

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
    TeachersManagement teachers;

    @Autowired
    PasswordEncoder passwordEncoder;

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

    private UUID createTeacher(Cookie admin, CreateTeacherRequest request) throws Exception {
        var result = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
    }

    @Test
    void createsAccountAndProfileInOneStep() throws Exception {
        var admin = signIn("teacher-profile-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        createTeacher(admin, new CreateTeacherRequest("teacher-one-step@eduerp.local", "Cô Lan", null,
                List.of("Tiếng Anh", "IELTS"), "0900000000", "Giáo viên IELTS 8.0"));

        var account = accounts.findByTypedEmail("teacher-one-step@eduerp.local").orElseThrow();
        assertThat(access.grants(account.getId(), AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ))
                .isTrue();
        assertThat(teachers.teacherProfileExists(account.getId())).isTrue();

        var listResult = mockMvc.perform(get("/api/teachers/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getContentAsString())
                .contains("teacher-one-step@eduerp.local", "IELTS");
    }

    @Test
    void updatesATeacherProfile() throws Exception {
        var admin = signIn("teacher-profile-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var profileId = createTeacher(admin, new CreateTeacherRequest("teacher-profile-2@eduerp.local", "Thầy Nam",
                null, List.of("Toán"), null, null));

        var updateResult = mockMvc.perform(patch("/api/teachers/profiles/" + profileId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTeacherProfileRequest(List.of("Toán", "Lý"), "0911111111", "Cập nhật", false))))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);

        var getResult = mockMvc.perform(get("/api/teachers/profiles/" + profileId).cookie(admin)).andReturn();
        assertThat(getResult.getResponse().getContentAsString()).contains("\"active\":false", "Lý");
    }

    @Test
    void getsASingleTeacherProfileById() throws Exception {
        var admin = signIn("teacher-profile-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var profileId = createTeacher(admin, new CreateTeacherRequest("teacher-profile-3@eduerp.local", "Cô Hoa",
                null, List.of("Hoá"), "0922222222", "Mô tả"));

        var result = mockMvc.perform(get("/api/teachers/profiles/" + profileId).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("teacher-profile-3@eduerp.local", "Hoá");
    }

    @Test
    void returns404ForAnUnknownTeacherProfileId() throws Exception {
        var admin = signIn("teacher-profile-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(get("/api/teachers/profiles/" + UUID.randomUUID()).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("TEACHERS_PROFILE_NOT_FOUND");
    }
}
