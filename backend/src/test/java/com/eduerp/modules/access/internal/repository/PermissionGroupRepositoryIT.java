package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.internal.model.Permission;
import com.eduerp.modules.access.internal.model.PermissionGroup;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class PermissionGroupRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PermissionRepository permissions;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Test
    void savesGroupWithScopedItems() {
        var permission = permissions.save(new Permission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.CREATE));
        var group = new PermissionGroup("Quản trị tài khoản", "Tạo/sửa tài khoản");
        group.addItem(permission, AccessConstants.PermissionScope.BRANCH);

        permissionGroups.save(group);
        var found = permissionGroups.findById(group.getId()).orElseThrow();

        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).getScope()).isEqualTo(AccessConstants.PermissionScope.BRANCH);
        assertThat(found.getItems().get(0).getPermission().getResource()).isEqualTo(AccessConstants.Resources.ACCOUNT);
    }
}
