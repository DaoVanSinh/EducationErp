package com.eduerp.modules.teachers.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.teachers.dto.CreateTeacherProfileRequest;
import com.eduerp.modules.teachers.dto.UpdateTeacherProfileRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
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
    PasswordEncoder passwordEncoder;

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

    private UUID teacherAccount(String email) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var saved = accounts.save(account);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);
        return saved.getId();
    }

    @Test
    void createsThenListsATeacherProfile() throws Exception {
        var admin = signIn("teacher-profile-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherAccountId = teacherAccount("teacher-profile-1@eduerp.local");

        var request = new CreateTeacherProfileRequest(teacherAccountId, List.of("Tiếng Anh", "IELTS"),
                "0900000000", "Giáo viên IELTS 8.0");
        var createResult = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/teachers/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(listResult.getResponse().getContentAsString()).contains("teacher-profile-1@eduerp.local");
    }

    @Test
    void rejectsCreatingAProfileForAnAccountWithoutTeacherRole() throws Exception {
        var admin = signIn("teacher-profile-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentAccount = new Account("wrong-role@eduerp.local", passwordEncoder.encode(PASSWORD), "Sai vai trò", null);
        studentAccount.recordFirstLogin();
        var saved = accounts.save(studentAccount);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.STUDENT);

        var request = new CreateTeacherProfileRequest(saved.getId(), List.of("Toán"), null, null);
        var result = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void updatesATeacherProfile() throws Exception {
        var admin = signIn("teacher-profile-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherAccountId = teacherAccount("teacher-profile-2@eduerp.local");
        var createResult = mockMvc.perform(post("/api/teachers/profiles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTeacherProfileRequest(teacherAccountId, List.of("Toán"), null, null))))
                .andReturn();
        var profileId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var updateResult = mockMvc.perform(patch("/api/teachers/profiles/" + profileId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTeacherProfileRequest(List.of("Toán", "Lý"), "0911111111", "Cập nhật", false))))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);

        var listResult = mockMvc.perform(get("/api/teachers/profiles").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("\"active\":false");
    }
}
