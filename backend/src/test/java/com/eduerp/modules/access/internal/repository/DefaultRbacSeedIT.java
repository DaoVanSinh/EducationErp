package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.AccessConstants;
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
class DefaultRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    @Test
    void adminRoleHasOrganizationScopeOnEveryResource() {
        var admin = roles.findByCode(AccessConstants.RoleCodes.ADMIN).orElseThrow();

        var allOrganizationScope = admin.getPermissionGroups().stream()
                .flatMap(g -> g.getItems().stream())
                .allMatch(item -> item.getScope() == AccessConstants.PermissionScope.ORGANIZATION);

        assertThat(allOrganizationScope).isTrue();
        assertThat(roles.findByCode(AccessConstants.RoleCodes.TEACHER)).isPresent();
        assertThat(roles.findByCode(AccessConstants.RoleCodes.STUDENT)).isPresent();
    }
}
