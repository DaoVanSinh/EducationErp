package com.eduerp.modules.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.access.dto.CreateRoleRequest;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.internal.model.ClassFixtures;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Spec 6.4 lớp 2: một vai trò TỰ TẠO ("Quản lý chi nhánh") chỉ có quyền ở scope BRANCH vẫn vào được các
 * màn vận hành, nhưng chỉ thấy và thao tác dữ liệu của chi nhánh chủ quản - client gửi chi nhánh khác
 * cũng không lọt.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BranchScopeIT {

    private static final String PASSWORD = "Password123!";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of(CoursesConstants.Schedules.CENTER_ZONE_ID));

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
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Branch branchA;
    private Branch branchB;
    private Course course;
    private UUID teacherId;
    private UUID managerRoleId;

    private UUID account(String email, UUID homeBranchId) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, homeBranchId);
        account.recordFirstLogin();
        return accounts.save(account).getId();
    }

    private Cookie signIn(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andReturn().getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private JsonNode getJson(Cookie cookie, String path) throws Exception {
        var result = mockMvc.perform(get(path).cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @BeforeEach
    void seedTwoBranchesAndABranchManagerRole() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 6);
        branchA = branches.save(new Branch("BA-" + suffix, "Chi nhánh A " + suffix, null));
        branchB = branches.save(new Branch("BB-" + suffix, "Chi nhánh B " + suffix, null));
        course = courses.save(new Course("BS-" + suffix, "Khoá chi nhánh " + suffix, null, null, null));
        teacherId = account("bs-teacher-" + suffix + "@eduerp.local", branchA.getId());
        access.assignRole(teacherId, AccessConstants.RoleCodes.TEACHER);
        classes.save(ClassFixtures.inProgress(course, "BS-A-" + suffix, branchA.getId(), teacherId, 20));
        classes.save(ClassFixtures.inProgress(course, "BS-B-" + suffix, branchB.getId(), teacherId, 20));

        var adminEmail = "bs-admin-" + suffix + "@eduerp.local";
        access.assignRole(account(adminEmail, null), AccessConstants.RoleCodes.ADMIN);
        var admin = signIn(adminEmail);
        var catalog = getJson(admin, "/api/rbac/catalog");
        var items = new ArrayList<CreatePermissionGroupRequest.Item>();
        for (var permission : catalog.get("permissions")) {
            var resource = permission.get("resource").asText();
            var action = permission.get("action").asText();
            if (isBranchManagerPermission(resource, action)) {
                items.add(new CreatePermissionGroupRequest.Item(UUID.fromString(permission.get("id").asText()),
                        AccessConstants.PermissionScope.BRANCH));
            }
        }
        var groupId = objectMapper.readValue(mockMvc.perform(post("/api/rbac/permission-groups").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePermissionGroupRequest(
                                "Vận hành chi nhánh " + suffix, null, items))))
                .andReturn().getResponse().getContentAsString(), UUID.class);
        managerRoleId = objectMapper.readValue(mockMvc.perform(post("/api/rbac/roles").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRoleRequest("BRANCH_MANAGER_" + suffix,
                                "Quản lý chi nhánh " + suffix, List.of(groupId)))))
                .andReturn().getResponse().getContentAsString(), UUID.class);
    }

    private static boolean isBranchManagerPermission(String resource, String action) {
        return List.of(AccessConstants.Resources.CLASS, AccessConstants.Resources.ACCOUNT,
                        AccessConstants.Resources.DASHBOARD, AccessConstants.Resources.ENROLLMENT)
                .contains(resource)
                && !AccessConstants.Actions.DELETE.equals(action);
    }

    private Cookie branchManager(String email, UUID homeBranchId) throws Exception {
        access.assignRole(account(email, homeBranchId), managerRoleId);
        return signIn(email);
    }

    @Test
    void seesOnlyTheClassesOfTheirOwnBranchEvenWhenAskingForAnother() throws Exception {
        var manager = branchManager("bs-manager-list@eduerp.local", branchA.getId());

        var mine = getJson(manager, "/api/courses/classes?size=100");
        var askedForB = getJson(manager, "/api/courses/classes?size=100&branchId=" + branchB.getId());

        assertThat(mine.get("items").findValuesAsText("branchId")).isNotEmpty()
                .allMatch(branchId -> branchId.equals(branchA.getId().toString()));
        assertThat(askedForB.get("items").findValuesAsText("branchId"))
                .allMatch(branchId -> branchId.equals(branchA.getId().toString()));
    }

    @Test
    void opensAClassOnlyInTheirOwnBranch() throws Exception {
        var manager = branchManager("bs-manager-open@eduerp.local", branchA.getId());
        var code = "BS-NEW-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        var inB = mockMvc.perform(post("/api/courses/classes").cookie(manager).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClassRequest(course.getId(), code + "B",
                                branchB.getId(), teacherId, 20, 1, TODAY, null, List.of()))))
                .andReturn();
        var inA = mockMvc.perform(post("/api/courses/classes").cookie(manager).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClassRequest(course.getId(), code + "A",
                                branchA.getId(), teacherId, 20, 1, TODAY, null, List.of()))))
                .andReturn();

        assertThat(inB.getResponse().getStatus()).isEqualTo(403);
        assertThat(inB.getResponse().getContentAsString()).contains("ACCESS_OUT_OF_BRANCH_SCOPE");
        assertThat(inA.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void seesOnlyAccountsAndDashboardOfTheirOwnBranch() throws Exception {
        var manager = branchManager("bs-manager-accounts@eduerp.local", branchA.getId());
        account("bs-other-branch-user@eduerp.local", branchB.getId());

        var accountsPage = getJson(manager, "/api/rbac/accounts?size=100&branchId=" + branchB.getId());

        assertThat(accountsPage.get("items").findValuesAsText("branchId"))
                .isNotEmpty()
                .allMatch(branchId -> branchId.equals(branchA.getId().toString()));
        getJson(manager, "/api/dashboard/stats");
    }

    @Test
    void cannotTouchSystemConfigurationWithBranchScope() throws Exception {
        var manager = branchManager("bs-manager-config@eduerp.local", branchA.getId());

        assertThat(mockMvc.perform(get("/api/rbac/catalog").cookie(manager)).andReturn().getResponse().getStatus())
                .as("vai trò/nhóm quyền vẫn chỉ dành cho scope toàn tổ chức").isEqualTo(403);
    }

    @Test
    void aBranchScopedAccountWithoutAHomeBranchSeesNothing() throws Exception {
        var orphan = branchManager("bs-manager-orphan@eduerp.local", null);

        var result = mockMvc.perform(get("/api/courses/classes").cookie(orphan)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentAsString()).contains("ACCESS_NO_HOME_BRANCH");
    }
}
