package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
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

/**
 * {@code account_roles}/{@code account_groups} có FK mức DB tới {@code accounts.id} (V8), nên test
 * này vẫn cần một dòng account thật để gán role — tạo qua {@code AccountRepository} của identity vì
 * đây là test tích hợp toàn context, không phải một import sản xuất xuyên module.
 */
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
    AccessManagement access;

    @Autowired
    AccountRepository accounts;

    @Test
    void computesOnMissThenServesFromCacheAfterEviction() {
        var account = accounts.save(new Account("cache-test@eduerp.local", "hash", "Cache Test", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.ADMIN);

        var first = cache.getEffectivePermissions(account.getId());
        assertThat(first).isNotEmpty();

        cache.evict(account.getId());
        var second = cache.getEffectivePermissions(account.getId());
        assertThat(second).isEqualTo(first);
    }

    @Test
    void servesFromCacheOnSecondCallWithoutEviction() {
        var account = accounts.save(new Account("cache-hit-test@eduerp.local", "hash", "Cache Hit Test", null));
        access.assignRole(account.getId(), AccessConstants.RoleCodes.ADMIN);

        var first = cache.getEffectivePermissions(account.getId());
        var second = cache.getEffectivePermissions(account.getId());

        assertThat(second).isEqualTo(first);
    }
}
