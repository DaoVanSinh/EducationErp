package com.eduerp.identity;

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
class RoleRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    @Autowired
    PermissionGroupRepository permissionGroups;

    @Test
    void roleCanReferenceMultiplePermissionGroups() {
        var group = permissionGroups.save(new PermissionGroup("Nhóm cơ bản", null));
        var role = new Role(IdentityConstants.RoleCodes.TEACHER, "Giáo viên", true);
        role.addPermissionGroup(group);

        roles.save(role);
        var found = roles.findByCode(IdentityConstants.RoleCodes.TEACHER).orElseThrow();

        assertThat(found.getPermissionGroups()).hasSize(1);
        assertThat(found.isSystemDefault()).isTrue();
    }
}
