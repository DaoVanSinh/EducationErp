package com.eduerp.modules.identity.internal.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.UUID;
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
class TokenBlacklistServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    TokenBlacklistService blacklist;

    @Test
    void blacklistedJtiIsDetected() {
        String jti = UUID.randomUUID().toString();

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
        blacklist.blacklist(jti, Duration.ofMinutes(1));
        assertThat(blacklist.isBlacklisted(jti)).isTrue();
    }

    @Test
    void blacklistAllActiveSessionsCoversTrackedJtis() {
        UUID accountId = UUID.randomUUID();
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();
        blacklist.trackSession(accountId, jti1, Duration.ofMinutes(15));
        blacklist.trackSession(accountId, jti2, Duration.ofMinutes(15));

        blacklist.blacklistAllActiveSessions(accountId);

        assertThat(blacklist.isBlacklisted(jti1)).isTrue();
        assertThat(blacklist.isBlacklisted(jti2)).isTrue();
    }
}
