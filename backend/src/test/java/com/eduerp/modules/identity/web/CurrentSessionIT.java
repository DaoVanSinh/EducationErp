package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.dto.AssignGroupRequest;
import com.eduerp.modules.access.internal.model.Group;
import com.eduerp.modules.access.internal.repository.GroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.SessionResponse;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
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
class CurrentSessionIT {

    private static final String PASSWORD = "Password123!";
    private static final UUID FULL_RIGHTS_PERMISSION_GROUP =
            UUID.fromString("11111111-0000-0000-0000-000000000001");

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
    GroupRepository groups;

    @Autowired
    BranchRepository branches;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode, Branch branch) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), "Người dùng " + email,
                branch == null ? null : branch.getId());
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
    void describesWhoIsSignedInAndWhatTheyMayDo() throws Exception {
        var branch = branches.save(new Branch("HN02", "Chi nhánh Hà Nội 2", null));
        var cookie = signIn("me-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN, branch);

        var result = mockMvc.perform(get("/api/account/me").cookie(cookie)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var session = objectMapper.readValue(result.getResponse().getContentAsString(), SessionResponse.class);
        assertThat(session.email()).isEqualTo("me-admin@eduerp.local");
        assertThat(session.roleCode()).isEqualTo(AccessConstants.RoleCodes.ADMIN);
        assertThat(session.branchName()).isEqualTo("Chi nhánh Hà Nội 2");
        assertThat(session.permissions())
                .anyMatch(p -> AccessConstants.Resources.DASHBOARD.equals(p.resource())
                        && AccessConstants.Actions.READ.equals(p.action())
                        && p.scope() == AccessConstants.PermissionScope.ORGANIZATION);
    }

    /**
     * Quyền trả về ở đây phải là quyền mà backend thật sự dùng — nếu lệch, giao diện sẽ hiện nút mà
     * API từ chối, hoặc giấu nút mà lẽ ra bấm được. Cache evict chạy trong cùng transaction (không
     * qua event bất đồng bộ) nên không cần đợi.
     */
    @Test
    void reflectsAPermissionChangeOnTheNextCall() throws Exception {
        var admin = signIn("me-granter@eduerp.local", AccessConstants.RoleCodes.ADMIN, null);
        var studentCookie = signIn("me-student@eduerp.local", AccessConstants.RoleCodes.STUDENT, null);
        var studentId = accounts.findByTypedEmail("me-student@eduerp.local").orElseThrow().getId();
        var group = new Group("Ban giám hiệu", null);
        group.addPermissionGroup(permissionGroups.findById(FULL_RIGHTS_PERMISSION_GROUP).orElseThrow());
        groups.save(group);

        var before = objectMapper.readValue(
                mockMvc.perform(get("/api/account/me").cookie(studentCookie)).andReturn()
                        .getResponse().getContentAsString(), SessionResponse.class);
        mockMvc.perform(post("/api/rbac/accounts/" + studentId + "/groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(group.getId()))))
                .andReturn();
        var after = objectMapper.readValue(
                mockMvc.perform(get("/api/account/me").cookie(studentCookie)).andReturn()
                        .getResponse().getContentAsString(), SessionResponse.class);

        assertThat(before.permissions()).noneMatch(p -> AccessConstants.Resources.ROLE.equals(p.resource()));
        assertThat(after.permissions()).anyMatch(p -> AccessConstants.Resources.ROLE.equals(p.resource()));
    }

    @Test
    void refusesAnAnonymousCaller() throws Exception {
        assertThat(mockMvc.perform(get("/api/account/me")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
