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

/** Mirror {@code DefaultRbacSeedIT}: quyền mới phải thực sự nằm trong nhóm "Toàn quyền hệ thống",
 * nếu không admin đăng nhập xong vẫn nhận 403 ở mọi endpoint của Phân hệ 3. */
@Testcontainers
@DataJpaTest
class EnrollmentBillingRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    private List<String> adminPermissionKeys() {
        return roles.findByCode(AccessConstants.RoleCodes.ADMIN).orElseThrow()
                .getPermissionGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(item -> item.getPermission().getResource() + ":" + item.getPermission().getAction() + ":"
                        + item.getScope().name())
                .toList();
    }

    @Test
    void adminHasOrganizationScopedEnrollmentPermissions() {
        assertThat(adminPermissionKeys()).contains(
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.CREATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.READ + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.UPDATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION);
    }

    @Test
    void adminHasOrganizationScopedInvoicePermissions() {
        assertThat(adminPermissionKeys()).contains(
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.CREATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.READ + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.UPDATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION);
    }

    /** Phân hệ 3 không có luồng duyệt - không seed APPROVE cho hai resource này (spec mục 7). */
    @Test
    void neitherResourceGetsAnApprovePermission() {
        assertThat(adminPermissionKeys()).noneMatch(key -> key.startsWith(
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.APPROVE));
        assertThat(adminPermissionKeys()).noneMatch(key -> key.startsWith(
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.APPROVE));
    }
}
