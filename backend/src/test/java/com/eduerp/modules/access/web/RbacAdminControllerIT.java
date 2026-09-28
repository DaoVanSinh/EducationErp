package com.eduerp.modules.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.dto.AssignGroupRequest;
import com.eduerp.modules.access.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.access.dto.CreateRoleRequest;
import com.eduerp.modules.access.internal.model.Group;
import com.eduerp.modules.access.internal.repository.GroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
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
    GroupRepository groups;

    @Autowired
    PermissionRepository permissions;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Autowired
    RoleRepository roles;

    @Autowired
    AccessManagement access;

    @Autowired
    AuditManagement audit;

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
    void assigningGroupWidensTheAccountsPermissionsImmediately() throws Exception {
        var admin = signIn("rbac-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacherAccount = accounts.save(new Account("rbac-teacher@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Rbac Teacher", null));
        access.assignRole(teacherAccount.getId(), AccessConstants.RoleCodes.TEACHER);
        var group = new Group("Phòng Kế toán", null);
        group.addPermissionGroup(permissionGroups.findById(FULL_RIGHTS_PERMISSION_GROUP).orElseThrow());
        groups.save(group);
        // Nạp cache trước khi gán: nếu use case quên evict, phép so sánh cuối cùng sẽ vẫn thấy bộ quyền cũ.
        assertThat(access.effectivePermissions(teacherAccount.getId()))
                .noneMatch(p -> AccessConstants.Resources.BRANCH.equals(p.resource()));

        var result = mockMvc.perform(post("/api/rbac/accounts/" + teacherAccount.getId() + "/groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(group.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(access.effectivePermissions(teacherAccount.getId()))
                .anyMatch(p -> AccessConstants.Resources.BRANCH.equals(p.resource())
                        && AccessConstants.Actions.CREATE.equals(p.action())
                        && p.scope() == AccessConstants.PermissionScope.ORGANIZATION);
        awaitAuditEntityId(AuditConstants.EntityTypes.ACCOUNT, AuditConstants.Actions.ACCOUNT_JOIN_GROUP,
                teacherAccount.getId().toString(), true);
    }

    @Test
    void createsAPermissionGroupThenARoleUsingIt() throws Exception {
        var admin = signIn("rbac-create@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var permission = permissions
                .findByResourceAndAction(AccessConstants.Resources.BRANCH, AccessConstants.Actions.READ)
                .orElseThrow();

        var groupResult = mockMvc.perform(post("/api/rbac/permission-groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePermissionGroupRequest(
                                "Xem chi nhánh", "Chỉ đọc chi nhánh của mình",
                                List.of(new CreatePermissionGroupRequest.Item(permission.getId(),
                                        AccessConstants.PermissionScope.BRANCH))))))
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
        awaitAuditEntityId(AuditConstants.EntityTypes.ROLE, AuditConstants.Actions.ROLE_CREATE,
                createdRole.getId().toString(), true);
    }

    /**
     * Quyền chỉnh RBAC không được rơi vào tay Role thường, kể cả khi đã đăng nhập hợp lệ: TEACHER
     * có ACCOUNT:UPDATE nhưng chỉ ở scope PERSONAL (để tự sửa hồ sơ), không đủ để sửa người khác.
     */
    @Test
    void refusesAnAccountWhosePermissionIsOnlyPersonal() throws Exception {
        var teacher = signIn("rbac-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var group = groups.save(new Group("Phòng Hành chính", null));
        var targetAccount = accounts.save(new Account("rbac-target@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Rbac Target", null));
        access.assignRole(targetAccount.getId(), AccessConstants.RoleCodes.STUDENT);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + targetAccount.getId() + "/groups")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(group.getId()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        // Không có gì bất đồng bộ để đợi ở đây (request bị 403 chặn trước khi use case chạy), nên
        // đọc thẳng — chỉ cần chắc chắn KHÔNG có dòng nào lọt qua.
        assertThat(audit.recentActions(AuditConstants.EntityTypes.ACCOUNT, AuditConstants.Actions.ACCOUNT_JOIN_GROUP, 20))
                .extracting(a -> a.entityId()).doesNotContain(targetAccount.getId().toString());
    }

    private void awaitAuditEntityId(String entityType, String action, String expectedEntityId, boolean present) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(entityType, action, 20).stream().map(a -> a.entityId()).toList();
            if (present) {
                assertThat(entityIds).contains(expectedEntityId);
            } else {
                assertThat(entityIds).doesNotContain(expectedEntityId);
            }
        });
    }

    @Test
    void listsTheReferenceCatalogForTheAdminScreens() throws Exception {
        var admin = signIn("rbac-read@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var catalog = mockMvc.perform(get("/api/rbac/catalog").cookie(admin)).andReturn();

        assertThat(catalog.getResponse().getStatus()).isEqualTo(200);
        var reference = objectMapper.readTree(catalog.getResponse().getContentAsString());
        assertThat(reference.get("roles")).isNotEmpty();
        assertThat(reference.get("permissions")).isNotEmpty();
        assertThat(reference.get("branches")).isNotNull();
    }

    @Test
    void rejectsAGroupIdThatDoesNotExist() throws Exception {
        var admin = signIn("rbac-missing@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var targetAccount = accounts.save(new Account("rbac-missing-target@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Rbac Missing", null));
        access.assignRole(targetAccount.getId(), AccessConstants.RoleCodes.STUDENT);

        var result = mockMvc.perform(post("/api/rbac/accounts/" + targetAccount.getId() + "/groups")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignGroupRequest(UUID.randomUUID()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("ACCESS_GROUP_NOT_FOUND");
    }
}
