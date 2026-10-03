package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.mockito.BDDMockito.given;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
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
class AccountAdminControllerIT {

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

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @MockBean
    JavaMailSender mailSender;

    /** MailClient dựng MimeMessage thật (email HTML) — mock trả về null nếu không stub việc này. */
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

    @Test
    void createsAnAccountAndSendsInvite() throws Exception {
        var admin = signIn("account-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateAccountRequest("new-teacher@eduerp.local", "Cô Lan", null,
                access.roleIdOf(AccessConstants.RoleCodes.TEACHER));

        var result = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var accountId = objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
        var saved = accounts.findById(accountId).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("new-teacher@eduerp.local");
        assertThat(saved.getLastLogin()).isNull();
        assertThat(access.roleOf(accountId)).isPresent();
        assertThat(stringRedisTemplate.hasKey("invite:account:" + accountId)).isTrue();
    }

    @Test
    void rejectsADuplicateEmail() throws Exception {
        var admin = signIn("account-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateAccountRequest("account-admin-2@eduerp.local", "Trùng email", null,
                access.roleIdOf(AccessConstants.RoleCodes.TEACHER));

        var result = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }
}
