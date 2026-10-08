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
 * Mirror {@code EnrollmentBillingRbacSeedIT}. Đây là lần ĐẦU TIÊN trong dự án một role khác ADMIN
 * được seed quyền cho một resource hoàn toàn mới (spec mục 8), nên test này chốt ba điều cùng lúc:
 * admin có GRADE ở ORGANIZATION, TEACHER có GRADE:READ/UPDATE ở PERSONAL, và STUDENT KHÔNG có gì.
 */
@Testcontainers
@DataJpaTest
class AcademicRbacSeedIT {

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

    private static String gradeKey(String action, String scope) {
        return AccessConstants.Resources.GRADE + ":" + action + ":" + scope;
    }

    /** Review Focus #5: admin phải có GRADE ở ORGANIZATION, nếu không guard sẽ coi admin như giáo
     * viên lạ và chặn họ ở mọi lớp họ không dạy. */
    @Test
    void adminHasOrganizationScopedGradePermissions() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.ADMIN)).contains(
                gradeKey(AccessConstants.Actions.CREATE, AccessConstants.ScopeNames.ORGANIZATION),
                gradeKey(AccessConstants.Actions.READ, AccessConstants.ScopeNames.ORGANIZATION),
                gradeKey(AccessConstants.Actions.UPDATE, AccessConstants.ScopeNames.ORGANIZATION));
    }

    /** Spec mục 6: TEACHER được GRADE:READ:PERSONAL + GRADE:UPDATE:PERSONAL - đủ để qua
     * {@code @PreAuthorize}, còn "đúng lớp hay không" do guard quyết định. */
    @Test
    void teacherHasPersonalScopedGradeReadAndUpdate() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(
                gradeKey(AccessConstants.Actions.READ, AccessConstants.PermissionScope.PERSONAL.name()),
                gradeKey(AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.PERSONAL.name()));
    }

    /** Giáo viên KHÔNG được tạo cấu hình điểm ở cấp tổ chức - CREATE chỉ dành cho admin (spec mục 8). */
    @Test
    void teacherDoesNotGetAnyOrganizationScopedGradePermission() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":")
                        && key.endsWith(":" + AccessConstants.ScopeNames.ORGANIZATION));
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":" + AccessConstants.Actions.CREATE));
    }

    /**
     * Global Constraint của spec mục 1: "học viên KHÔNG tự xem được điểm/tiến trình của mình qua hệ
     * thống ở giai đoạn này". V5 gắn group "Tự phục vụ cơ bản" cho CẢ TEACHER và STUDENT, nên nếu
     * V25 nhét GRADE vào group đó thì học viên cũng được cấp - test này biến sai sót đó thành build đỏ.
     */
    @Test
    void studentGetsNoGradePermissionAtAll() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":"));
    }

    /** Quyền tự phục vụ sẵn có của TEACHER/STUDENT không được mất đi khi thêm group mới. */
    @Test
    void keepsTheExistingSelfServicePermissionsOfBothRoles() {
        var personalAccountUpdate = AccessConstants.Resources.ACCOUNT + ":"
                + AccessConstants.Actions.UPDATE + ":" + AccessConstants.PermissionScope.PERSONAL.name();
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(personalAccountUpdate);
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).contains(personalAccountUpdate);
    }
}
