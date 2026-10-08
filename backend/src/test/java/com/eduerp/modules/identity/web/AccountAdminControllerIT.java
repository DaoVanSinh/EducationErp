package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.mockito.BDDMockito.given;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
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
    BranchRepository branches;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    com.eduerp.modules.audit.AuditManagement audit;

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
    void rejectsANonExistentRoleIdWithAProblemDetailNotA500() throws Exception {
        var admin = signIn("account-admin-bad-role@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateAccountRequest("bad-role@eduerp.local", "Role sai", null, UUID.randomUUID());

        var result = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("ACCESS_ROLE_NOT_FOUND");
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

    @Test
    void resendsAnInviteWithAFreshPassword() throws Exception {
        var admin = signIn("resend-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("resend-target@eduerp.local", "Thầy X", null,
                                        access.roleIdOf(AccessConstants.RoleCodes.TEACHER)))))
                .andReturn();
        var accountId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var passwordHashBeforeResend = accounts.findById(accountId).orElseThrow().getPasswordHash();

        var result = mockMvc.perform(post("/api/rbac/accounts/" + accountId + "/resend-invite")
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey("invite:account:" + accountId)).isTrue();
        // "Mật khẩu mới" phải thật sự mới - không chỉ Redis key được chạm vào mà hash mật khẩu đứng yên,
        // vì khi đó mật khẩu cũ (có thể đã lộ qua email trước) vẫn còn dùng được.
        var passwordHashAfterResend = accounts.findById(accountId).orElseThrow().getPasswordHash();
        assertThat(passwordHashAfterResend).isNotEqualTo(passwordHashBeforeResend);
    }

    @Test
    void revokesThenDisablesTheAccount() throws Exception {
        var admin = signIn("revoke-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("revoke-target@eduerp.local", "Thầy Y", null,
                                        access.roleIdOf(AccessConstants.RoleCodes.TEACHER)))))
                .andReturn();
        var accountId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + accountId + "/revoke-invite")
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey("invite:account:" + accountId)).isFalse();
        var disabled = accounts.findById(accountId).orElseThrow();
        assertThat(disabled.getStatus()).isEqualTo(com.eduerp.modules.identity.IdentityConstants.AccountStatus.DISABLED);
    }

    @Test
    void rejectsResendingAnInviteForAnAlreadyActivatedAccount() throws Exception {
        var admin = signIn("resend-active-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var active = new Account("already-active@eduerp.local", passwordEncoder.encode(PASSWORD), "Đã kích hoạt", null);
        active.recordFirstLogin();
        var saved = accounts.save(active);
        access.assignRole(saved.getId(), AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + saved.getId() + "/resend-invite")
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void listIncludesLastLoginAndAuditsAccountCreation() throws Exception {
        var admin = signIn("audit-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/rbac/accounts").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateAccountRequest("audited@eduerp.local", "Có audit", null,
                                        access.roleIdOf(AccessConstants.RoleCodes.TEACHER)))))
                .andReturn();
        var accountId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rbac/accounts")
                                .cookie(admin))
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString()).contains("\"lastLogin\":null");

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(audit.recentActions(
                                com.eduerp.modules.audit.AuditConstants.EntityTypes.ACCOUNT,
                                com.eduerp.modules.audit.AuditConstants.Actions.ACCOUNT_CREATE, 20))
                        .extracting(a -> a.entityId())
                        .contains(accountId.toString()));
    }

    @Test
    void transfersAnAccountToAnotherBranch() throws Exception {
        var admin = signIn("rbac-transfer-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var branch = branches.save(new Branch("HCM01", "Chi nhánh Hồ Chí Minh", null));
        var teacherAccount = accounts.save(new Account("rbac-transfer@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Rbac Transfer", null));
        access.assignRole(teacherAccount.getId(), AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + teacherAccount.getId() + "/transfer-branch")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferBranchRequest(branch.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accounts.findById(teacherAccount.getId()).orElseThrow().getHomeBranchId())
                .isEqualTo(branch.getId());
    }

    @Test
    void listsAccountsForTheAdminScreen() throws Exception {
        var admin = signIn("rbac-read@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var accountsPage = mockMvc.perform(get("/api/rbac/accounts?page=0&size=5").cookie(admin)).andReturn();

        assertThat(accountsPage.getResponse().getStatus()).isEqualTo(200);
        var page = objectMapper.readTree(accountsPage.getResponse().getContentAsString());
        assertThat(page.get("items")).hasSizeLessThanOrEqualTo(5);
        assertThat(page.get("totalItems").asLong()).isEqualTo(accounts.count());
        assertThat(page.get("items").get(0).hasNonNull("roleCode")).isTrue();
    }

    @Test
    void filtersAccountsByBranchId() throws Exception {
        var admin = signIn("rbac-branch-filter-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var branch = branches.save(new Branch("DN02", "Chi nhánh lọc", null));
        var inBranch = accounts.save(new Account("rbac-branch-filter-in@eduerp.local",
                passwordEncoder.encode(PASSWORD), "In Branch", branch.getId()));
        accounts.save(new Account("rbac-branch-filter-out@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Out Of Branch", null));

        var result = mockMvc.perform(get("/api/rbac/accounts?branchId=" + branch.getId()).cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("totalItems").asLong()).isEqualTo(1);
        assertThat(page.get("items").get(0).get("id").asText()).isEqualTo(inBranch.getId().toString());
    }

    /** Danh sách tài khoản lộ dữ liệu cả tổ chức, nên quyền PERSONAL không được mở. */
    @Test
    void refusesToListAccountsForAPersonalScopedRole() throws Exception {
        var teacher = signIn("rbac-read-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(get("/api/rbac/accounts").cookie(teacher)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
