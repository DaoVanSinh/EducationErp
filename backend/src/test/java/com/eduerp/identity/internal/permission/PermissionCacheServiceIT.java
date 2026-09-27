package com.eduerp.identity.internal.permission;

import com.eduerp.identity.IdentityConstants;
import com.eduerp.identity.internal.model.Account;
import com.eduerp.identity.internal.repository.AccountRepository;
import com.eduerp.identity.internal.repository.RoleRepository;
import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class PermissionCacheServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    PermissionCacheService cache;

    @Autowired
    AccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Test
    void computesOnMissThenServesFromCacheAfterEviction() {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        var account = accounts.save(new Account("cache-test@eduerp.local", "hash", "Cache Test", role, null));

        var first = cache.getEffectivePermissions(account.getId());
        assertThat(first).isNotEmpty();

        cache.evict(account.getId());
        var second = cache.getEffectivePermissions(account.getId());
        assertThat(second).isEqualTo(first);
    }

    @Test
    void servesFromCacheOnSecondCallWithoutEviction() {
        var role = roles.findByCode(IdentityConstants.RoleCodes.ADMIN).orElseThrow();
        var account = accounts.save(new Account("cache-hit-test@eduerp.local", "hash", "Cache Hit Test", role, null));

        var first = cache.getEffectivePermissions(account.getId());
        var second = cache.getEffectivePermissions(account.getId());

        assertThat(second).isEqualTo(first);
    }
}
