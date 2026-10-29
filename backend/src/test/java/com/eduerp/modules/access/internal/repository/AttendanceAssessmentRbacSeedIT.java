package com.eduerp.modules.access.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V30: admin có ATTENDANCE/ASSIGNMENT toàn tổ chức, giáo viên có ở scope PERSONAL, học viên CHỈ có quyền
 * nộp lượt làm mini-task (PRACTICE:CREATE) - không điểm danh hay giao bài được.
 */
@Testcontainers
@DataJpaTest
class AttendanceAssessmentRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    private List<String> permissionKeysOf(String roleCode) {
        return roles.findByCode(roleCode).orElseThrow()
                .getPermissionGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(item -> item.getPermission().getResource() + ":" + item.getPermission().getAction() + ":"
                        + item.getScope().name())
                .toList();
    }

    private static String key(String resource, String action, AccessConstants.PermissionScope scope) {
        return resource + ":" + action + ":" + scope.name();
    }

    @Test
    void adminManagesAttendanceAndAssignmentsEverywhereButDoesNotPractise() {
        var keys = permissionKeysOf(AccessConstants.RoleCodes.ADMIN);
        var org = AccessConstants.PermissionScope.ORGANIZATION;
        assertThat(keys).contains(
                key(AccessConstants.Resources.ATTENDANCE, AccessConstants.Actions.UPDATE, org),
                key(AccessConstants.Resources.ASSIGNMENT, AccessConstants.Actions.CREATE, org));
        assertThat(keys).noneMatch(item -> item.startsWith(AccessConstants.Resources.PRACTICE + ":"));
    }

    @Test
    void teacherGetsAttendanceAndAssignmentsAtPersonalScope() {
        var keys = permissionKeysOf(AccessConstants.RoleCodes.TEACHER);
        var personal = AccessConstants.PermissionScope.PERSONAL;
        assertThat(keys).contains(
                key(AccessConstants.Resources.ATTENDANCE, AccessConstants.Actions.READ, personal),
                key(AccessConstants.Resources.ATTENDANCE, AccessConstants.Actions.UPDATE, personal),
                key(AccessConstants.Resources.ASSIGNMENT, AccessConstants.Actions.CREATE, personal),
                key(AccessConstants.Resources.ASSIGNMENT, AccessConstants.Actions.UPDATE, personal));
        assertThat(keys).noneMatch(item -> item.startsWith(AccessConstants.Resources.PRACTICE + ":"));
    }

    @Test
    void studentOnlyGetsToSubmitPracticeAttempts() {
        var keys = permissionKeysOf(AccessConstants.RoleCodes.STUDENT);
        assertThat(keys).contains(key(AccessConstants.Resources.PRACTICE, AccessConstants.Actions.CREATE,
                AccessConstants.PermissionScope.PERSONAL));
        assertThat(keys).noneMatch(item -> item.startsWith(AccessConstants.Resources.ATTENDANCE + ":")
                || item.startsWith(AccessConstants.Resources.ASSIGNMENT + ":"));
    }
}
