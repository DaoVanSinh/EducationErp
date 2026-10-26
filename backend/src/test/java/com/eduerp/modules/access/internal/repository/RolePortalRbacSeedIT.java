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
 * V27 - cổng theo vai trò: TEACHER có TEACHING:READ, STUDENT có LEARNING:READ, mỗi role CHỈ có cổng
 * của mình, và ADMIN không nhận cổng nào (đó là màn hình "của tôi", admin không dạy/học lớp nào).
 */
@Testcontainers
@DataJpaTest
class RolePortalRbacSeedIT {

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

    private static String personalRead(String resource) {
        return resource + ":" + AccessConstants.Actions.READ + ":" + AccessConstants.PermissionScope.PERSONAL.name();
    }

    @Test
    void teacherGetsOnlyTheTeachingPortal() {
        var keys = permissionKeysOf(AccessConstants.RoleCodes.TEACHER);
        assertThat(keys).contains(personalRead(AccessConstants.Resources.TEACHING));
        assertThat(keys).noneMatch(key -> key.startsWith(AccessConstants.Resources.LEARNING + ":"));
    }

    @Test
    void studentGetsOnlyTheLearningPortal() {
        var keys = permissionKeysOf(AccessConstants.RoleCodes.STUDENT);
        assertThat(keys).contains(personalRead(AccessConstants.Resources.LEARNING));
        assertThat(keys).noneMatch(key -> key.startsWith(AccessConstants.Resources.TEACHING + ":"));
    }

    @Test
    void adminGetsNeitherPortal() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.ADMIN)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.TEACHING + ":")
                        || key.startsWith(AccessConstants.Resources.LEARNING + ":"));
    }
}
