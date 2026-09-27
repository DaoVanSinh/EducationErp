package com.eduerp.modules.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.AssignGroupRequest;
import com.eduerp.modules.identity.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.identity.dto.CreateRoleRequest;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.model.AuditLog;
import com.eduerp.modules.identity.internal.model.Branch;
import com.eduerp.modules.identity.internal.model.Group;
import com.eduerp.modules.identity.internal.permission.PermissionCacheService;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.AuditLogRepository;
import com.eduerp.modules.identity.internal.repository.BranchRepository;
import com.eduerp.modules.identity.internal.repository.GroupRepository;
import com.eduerp.modules.identity.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.identity.internal.repository.PermissionRepository;
import com.eduerp.modules.identity.internal.repository.RoleRepository;
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
import org.springframework.data.domain.Pageable;
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
class RbacAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    /** Nhóm quyền "Toàn quyền hệ thống" do V5__seed_default_rbac.sql dựng sẵn. */
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
    RoleRepository roles;

    @Autowired
    GroupRepository groups;

    @Autowired
    BranchRepository branches;

    @Autowired
    PermissionRepository permissions;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Autowired
    AuditLogRepository auditLogs;

    @Autowired
    PermissionCacheService permissionCache;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var role = roles.findByCode(roleCode).orElseThrow();
        accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, role, null));
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @Test
    void assigningGroupWidensTheAccountsPermissionsImmediately() throws Exception {
        var admin = signIn("rbac-admin@eduerp.local", IdentityConstants.RoleCodes.ADMIN);
        var teacher = accounts.save(new Account("rbac-teacher@eduerp.local", passwordEncoder.encode(PASSWORD),
                "Rbac Teacher", roles.findByCode(IdentityConstants.RoleCodes.TEACHER).orElseThrow(), null));
        var group = new Group("Phòng Kế toán", null);
        group.addPermissionGroup(permissionGroups.findById(FULL_RIGHTS_PERMISSION_GROUP).orElseThrow());
        groups.save(group);
        // Nạp cache trước khi gán: nếu use case quên evict, phép so sánh cuối cùng sẽ vẫn thấy bộ quyền cũ.
        assertThat(permissionCache.getEffectivePermissions(teacher.getId()))
                .noneMatch(p -> IdentityConstants.Resources.BRANCH.equals(p.resource()));

        var result = mockMvc.perform(post("/api/rbac/accounts/" + teacher.getId() + "/groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(group.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(permissionCache.getEffectivePermissions(teacher.getId()))
                .anyMatch(p -> IdentityConstants.Resources.BRANCH.equals(p.resource())
                        && IdentityConstants.Actions.CREATE.equals(p.action())
                        && p.scope() == IdentityConstants.PermissionScope.ORGANIZATION);
        assertThat(auditLogs.findByEntityTypeAndActionOrderByOccurredAtDesc(IdentityConstants.Resources.ACCOUNT,
                IdentityConstants.AuditActions.ACCOUNT_JOIN_GROUP, Pageable.unpaged()))
                .extracting(AuditLog::getEntityId).contains(teacher.getId().toString());
    }

    @Test
    void transfersAnAccountToAnotherBranch() throws Exception {
        var admin = signIn("rbac-transfer-admin@eduerp.local", IdentityConstants.RoleCodes.ADMIN);
        var branch = branches.save(new Branch("HCM01", "Chi nhánh Hồ Chí Minh", null));
        var teacher = accounts.save(new Account("rbac-transfer@eduerp.local", passwordEncoder.encode(PASSWORD),
                "Rbac Transfer", roles.findByCode(IdentityConstants.RoleCodes.TEACHER).orElseThrow(), null));

        var result = mockMvc.perform(post("/api/rbac/accounts/" + teacher.getId() + "/transfer-branch")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferBranchRequest(branch.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accounts.findById(teacher.getId()).orElseThrow().getHomeBranch().getId())
                .isEqualTo(branch.getId());
    }

    @Test
    void createsAPermissionGroupThenARoleUsingIt() throws Exception {
        var admin = signIn("rbac-create@eduerp.local", IdentityConstants.RoleCodes.ADMIN);
        var permission = permissions
                .findByResourceAndAction(IdentityConstants.Resources.BRANCH, IdentityConstants.Actions.READ)
                .orElseThrow();

        var groupResult = mockMvc.perform(post("/api/rbac/permission-groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePermissionGroupRequest(
                                "Xem chi nhánh", "Chỉ đọc chi nhánh của mình",
                                List.of(new CreatePermissionGroupRequest.Item(permission.getId(),
                                        IdentityConstants.PermissionScope.BRANCH))))))
                .andReturn();
        assertThat(groupResult.getResponse().getStatus()).isEqualTo(200);
        var permissionGroupId = objectMapper.readValue(groupResult.getResponse().getContentAsString(), UUID.class);

        var roleResult = mockMvc.perform(post("/api/rbac/roles")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoleRequest("BRANCH_VIEWER", "Người xem chi nhánh",
                                        List.of(permissionGroupId)))))
                .andReturn();

        assertThat(roleResult.getResponse().getStatus()).isEqualTo(200);
        var createdRole = roles.findByCode("BRANCH_VIEWER").orElseThrow();
        assertThat(createdRole.isSystemDefault()).isFalse();
        assertThat(auditLogs.findByEntityTypeAndActionOrderByOccurredAtDesc(IdentityConstants.Resources.ROLE,
                IdentityConstants.AuditActions.ROLE_CREATE, Pageable.unpaged()))
                .extracting(AuditLog::getEntityId).contains(createdRole.getId().toString());
    }

    /**
     * Quyền chỉnh RBAC không được rơi vào tay Role thường, kể cả khi đã đăng nhập hợp lệ: TEACHER
     * có ACCOUNT:UPDATE nhưng chỉ ở scope PERSONAL (để tự sửa hồ sơ), không đủ để sửa người khác.
     */
    @Test
    void refusesAnAccountWhosePermissionIsOnlyPersonal() throws Exception {
        var teacher = signIn("rbac-outsider@eduerp.local", IdentityConstants.RoleCodes.TEACHER);
        var group = groups.save(new Group("Phòng Hành chính", null));
        var target = accounts.save(new Account("rbac-target@eduerp.local", passwordEncoder.encode(PASSWORD),
                "Rbac Target", roles.findByCode(IdentityConstants.RoleCodes.STUDENT).orElseThrow(), null));

        var result = mockMvc.perform(post("/api/rbac/accounts/" + target.getId() + "/groups")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(group.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(auditLogs.findByEntityTypeAndActionOrderByOccurredAtDesc(IdentityConstants.Resources.ACCOUNT,
                IdentityConstants.AuditActions.ACCOUNT_JOIN_GROUP, Pageable.unpaged()))
                .extracting(AuditLog::getEntityId).doesNotContain(target.getId().toString());
    }

    @Test
    void rejectsAGroupIdThatDoesNotExist() throws Exception {
        var admin = signIn("rbac-missing@eduerp.local", IdentityConstants.RoleCodes.ADMIN);
        var target = accounts.save(new Account("rbac-missing-target@eduerp.local", passwordEncoder.encode(PASSWORD),
                "Rbac Missing", roles.findByCode(IdentityConstants.RoleCodes.STUDENT).orElseThrow(), null));

        var result = mockMvc.perform(post("/api/rbac/accounts/" + target.getId() + "/groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(UUID.randomUUID()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("IDENTITY_GROUP_NOT_FOUND");
    }
}
