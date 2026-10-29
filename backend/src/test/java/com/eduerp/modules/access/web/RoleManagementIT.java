package com.eduerp.modules.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.dto.ChangeAccountRoleRequest;
import com.eduerp.modules.access.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.access.dto.UpdatePermissionGroupRequest;
import com.eduerp.modules.access.dto.UpdateRoleRequest;
import com.eduerp.modules.courses.internal.model.ClassFixtures;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.StudentsManagement;
import com.eduerp.modules.teachers.TeachersManagement;
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

/** Đổi vai trò tài khoản, sửa vai trò, sửa nhóm quyền - và các chốt chặn đi kèm (spec mục 5, 6.4). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RoleManagementIT {

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
    TeachersManagement teachers;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID account(String email, String roleCode) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        return account.getId();
    }

    private Cookie signIn(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn();
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private Cookie admin(String suffix) throws Exception {
        var email = "role-admin-" + suffix + "@eduerp.local";
        account(email, AccessConstants.RoleCodes.ADMIN);
        return signIn(email);
    }

    private int changeRole(Cookie admin, UUID accountId, String roleCode) throws Exception {
        return mockMvc.perform(patch("/api/rbac/accounts/" + accountId + "/role").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeAccountRoleRequest(access.roleIdOf(roleCode)))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void fixesAStudentCreatedByMistakeIntoATeacherAndGivesThemATeacherProfile() throws Exception {
        var admin = admin("fix");
        var mistaken = account("role-mistaken@eduerp.local", AccessConstants.RoleCodes.STUDENT);
        assertThat(students.findProfileByAccountId(mistaken)).isPresent();

        assertThat(changeRole(admin, mistaken, AccessConstants.RoleCodes.TEACHER)).isEqualTo(200);

        assertThat(access.roleOf(mistaken)).map(AccessManagement.RoleSummary::code)
                .contains(AccessConstants.RoleCodes.TEACHER);
        assertThat(teachers.teacherProfileExists(mistaken)).isTrue();
        assertThat(students.findProfileByAccountId(mistaken))
                .hasValueSatisfying(profile -> assertThat(profile.active())
                        .as("hồ sơ học viên cũ còn đó nhưng ngừng hoạt động").isFalse());
        assertThat(access.grants(mistaken, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ))
                .as("cache quyền được xoá - năng lực mới có hiệu lực ngay").isTrue();
    }

    @Test
    void refusesToTakeTheTeachingCapabilityFromSomeoneStillTeachingAnOpenClass() throws Exception {
        var admin = admin("busy");
        var teacher = account("role-busy-teacher@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var course = courses.save(new Course("ROLE-C", "Khoá đổi vai trò", null, null, null));
        var branch = branches.save(new Branch("ROLE-B", "Chi nhánh đổi vai trò", null));
        classes.save(ClassFixtures.inProgress(course, "ROLE-K1", branch.getId(), teacher, 20));

        var result = mockMvc.perform(patch("/api/rbac/accounts/" + teacher + "/role").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangeAccountRoleRequest(access.roleIdOf(AccessConstants.RoleCodes.STUDENT)))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("COURSES_TEACHER_HAS_OPEN_CLASSES");
        assertThat(access.roleOf(teacher)).map(AccessManagement.RoleSummary::code)
                .contains(AccessConstants.RoleCodes.TEACHER);
    }

    @Test
    void anAdminCannotChangeTheirOwnRole() throws Exception {
        var email = "role-self@eduerp.local";
        var me = account(email, AccessConstants.RoleCodes.ADMIN);

        assertThat(changeRole(signIn(email), me, AccessConstants.RoleCodes.STUDENT)).isEqualTo(409);
    }

    @Test
    void theSystemAdminRoleAndFullAccessGroupAreLocked() throws Exception {
        var admin = admin("locked");
        var adminRoleId = access.roleIdOf(AccessConstants.RoleCodes.ADMIN);

        var roleResult = mockMvc.perform(put("/api/rbac/roles/" + adminRoleId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Hết quyền", List.of()))))
                .andReturn();
        var groupResult = mockMvc.perform(put("/api/rbac/permission-groups/"
                                + AccessConstants.SystemPermissionGroups.FULL_ACCESS_ID).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdatePermissionGroupRequest("x", null,
                                List.of(new CreatePermissionGroupRequest.Item(UUID.randomUUID(),
                                        AccessConstants.PermissionScope.PERSONAL))))))
                .andReturn();

        assertThat(roleResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(groupResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(roleResult.getResponse().getContentAsString()).contains("ACCESS_SYSTEM_ADMIN_LOCKED");
    }

    @Test
    void editingARoleChangesWhatItsHoldersCanDoOnTheirNextRequest() throws Exception {
        var admin = admin("edit");
        var catalog = objectMapper.readTree(mockMvc.perform(get("/api/rbac/catalog").cookie(admin)).andReturn()
                .getResponse().getContentAsString());
        var dashboardPermission = findPermission(catalog, AccessConstants.Resources.DASHBOARD,
                AccessConstants.Actions.READ);

        var groupId = objectMapper.readValue(mockMvc.perform(post("/api/rbac/permission-groups").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePermissionGroupRequest("Xem tổng quan",
                                null, List.of(new CreatePermissionGroupRequest.Item(dashboardPermission,
                                        AccessConstants.PermissionScope.ORGANIZATION))))))
                .andReturn().getResponse().getContentAsString(), UUID.class);
        var teacherEmail = "role-edit-teacher@eduerp.local";
        account(teacherEmail, AccessConstants.RoleCodes.TEACHER);
        var teacher = signIn(teacherEmail);
        assertThat(mockMvc.perform(get("/api/dashboard/stats").cookie(teacher)).andReturn().getResponse().getStatus())
                .isEqualTo(403);

        var teacherRoleId = access.roleIdOf(AccessConstants.RoleCodes.TEACHER);
        var currentGroups = new java.util.ArrayList<UUID>();
        for (var role : catalog.get("roles")) {
            if (role.get("id").asText().equals(teacherRoleId.toString())) {
                role.get("permissionGroupIds").forEach(id -> currentGroups.add(UUID.fromString(id.asText())));
            }
        }
        currentGroups.add(groupId);
        var update = mockMvc.perform(put("/api/rbac/roles/" + teacherRoleId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest("Giáo viên", currentGroups))))
                .andReturn();

        assertThat(update.getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/dashboard/stats").cookie(teacher)).andReturn().getResponse().getStatus())
                .as("quyền mới có hiệu lực ngay, không cần đăng nhập lại").isEqualTo(200);
        var after = objectMapper.readTree(mockMvc.perform(get("/api/rbac/catalog").cookie(admin)).andReturn()
                .getResponse().getContentAsString());
        assertThat(after.toString()).contains(groupId.toString());
    }

    private UUID findPermission(com.fasterxml.jackson.databind.JsonNode catalog, String resource, String action) {
        for (var permission : catalog.get("permissions")) {
            if (permission.get("resource").asText().equals(resource)
                    && permission.get("action").asText().equals(action)) {
                return UUID.fromString(permission.get("id").asText());
            }
        }
        throw new AssertionError("permission " + resource + ":" + action + " not seeded");
    }
}
