package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
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

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, null));
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
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

    /** Danh sách tài khoản lộ dữ liệu cả tổ chức, nên quyền PERSONAL không được mở. */
    @Test
    void refusesToListAccountsForAPersonalScopedRole() throws Exception {
        var teacher = signIn("rbac-read-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(get("/api/rbac/accounts").cookie(teacher)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
