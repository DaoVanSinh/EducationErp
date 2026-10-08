package com.eduerp.modules.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.dto.CreateBranchRequest;
import com.eduerp.modules.organization.dto.UpdateBranchRequest;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
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
class BranchAdminControllerIT {

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
    AuditManagement audit;

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
    void createsThenListsABranch() throws Exception {
        var admin = signIn("branch-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var createResult = mockMvc.perform(post("/api/organization/branches")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBranchRequest("HCM01", "Chi nhánh Quận 1", "1 Nguyễn Huệ"))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var branchId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);
        awaitAuditEntityId(AuditConstants.Actions.BRANCH_CREATE, branchId.toString(), true);

        var listResult = mockMvc.perform(get("/api/organization/branches").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        assertThat(items).anyMatch(node -> branchId.toString().equals(node.get("id").asText()));
    }

    @Test
    void updatesABranchNameAddressAndActiveFlag() throws Exception {
        var admin = signIn("branch-editor@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var branch = branches.save(new Branch("DN01", "Chi nhánh Đà Nẵng", "1 Bạch Đằng"));

        var updateResult = mockMvc.perform(patch("/api/organization/branches/" + branch.getId())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateBranchRequest("Chi nhánh Đà Nẵng (mới)", "2 Bạch Đằng", false))))
                .andReturn();

        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);
        var updated = branches.findById(branch.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Chi nhánh Đà Nẵng (mới)");
        assertThat(updated.getAddress()).isEqualTo("2 Bạch Đằng");
        assertThat(updated.isActive()).isFalse();
        awaitAuditEntityId(AuditConstants.Actions.BRANCH_UPDATE, branch.getId().toString(), true);
    }

    @Test
    void rejectsUpdatingABranchThatDoesNotExist() throws Exception {
        var admin = signIn("branch-missing@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(patch("/api/organization/branches/" + UUID.randomUUID())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateBranchRequest("Không tồn tại", null, true))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("ORGANIZATION_BRANCH_NOT_FOUND");
    }

    @Test
    void rejectsBlankNameOnCreate() throws Exception {
        var admin = signIn("branch-invalid@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post("/api/organization/branches")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBranchRequest("HP01", "", null))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAnAccountWithOnlyPersonalScopePermissions() throws Exception {
        var teacher = signIn("branch-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/organization/branches")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBranchRequest("XX01", "Không được phép", null))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(branches.findByCode("XX01")).isEmpty();
    }

    private void awaitAuditEntityId(String action, String expectedEntityId, boolean present) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.BRANCH, action, 20).stream()
                    .map(a -> a.entityId()).toList();
            if (present) {
                assertThat(entityIds).contains(expectedEntityId);
            } else {
                assertThat(entityIds).doesNotContain(expectedEntityId);
            }
        });
    }
}
