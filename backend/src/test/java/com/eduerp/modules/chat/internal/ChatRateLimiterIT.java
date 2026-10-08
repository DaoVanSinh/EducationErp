package com.eduerp.modules.chat.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.TooManyMessagesException;
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis THẬT qua Testcontainers, mirror {@code PermissionCacheServiceIT}: một bộ đếm tần suất test
 * bằng mock chỉ chứng minh được là code gọi đúng method, không chứng minh được ngưỡng và TTL thật
 * hoạt động - và chính ngưỡng là thứ chặn hoá đơn API.
 */
@Testcontainers
@SpringBootTest
class ChatRateLimiterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    ChatRateLimiter limiter;

    @Autowired
    StringRedisTemplate redisTemplate;

    private static String keyOf(UUID accountId) {
        return CacheKeyBuilder.key(ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT, accountId);
    }

    /** Spec Ruling R3: đúng 10 câu hỏi/phút được phép, câu thứ 11 bị chặn. */
    @Test
    void allowsExactlyTheQuotaThenRefusesTheNextMessage() {
        var accountId = UUID.randomUUID();

        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE).forEach(index ->
                assertThatCode(() -> limiter.checkAndIncrement(accountId))
                        .as("câu hỏi thứ " + (index + 1) + " phải được phép")
                        .doesNotThrowAnyException());

        assertThatThrownBy(() -> limiter.checkAndIncrement(accountId))
                .isInstanceOf(TooManyMessagesException.class)
                .hasMessageContaining(accountId.toString());
    }

    /** Hạn mức là theo TÀI KHOẢN: một học viên dùng hết hạn mức không được làm người khác bị chặn. */
    @Test
    void countsEachAccountSeparately() {
        var noisy = UUID.randomUUID();
        var quiet = UUID.randomUUID();
        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE)
                .forEach(index -> limiter.checkAndIncrement(noisy));

        assertThatThrownBy(() -> limiter.checkAndIncrement(noisy))
                .isInstanceOf(TooManyMessagesException.class);
        assertThatCode(() -> limiter.checkAndIncrement(quiet)).doesNotThrowAnyException();
    }

    /**
     * Không có TTL thì bộ đếm là vĩnh viễn: một học viên hỏi 10 câu hôm nay sẽ bị chặn mãi mãi, và
     * Redis đầy dần theo số tài khoản. Đây là khẳng định quan trọng nhất của class này.
     */
    @Test
    void setsAOneMinuteExpiryOnTheFirstMessageOfAWindow() {
        var accountId = UUID.randomUUID();

        limiter.checkAndIncrement(accountId);

        var ttl = redisTemplate.getExpire(keyOf(accountId), TimeUnit.SECONDS);
        assertThat(ttl).as("key phải có TTL thật, -1 nghĩa là không bao giờ hết hạn")
                .isBetween(1L, ChatConstants.RateLimit.WINDOW.toSeconds());
    }

    /** Cửa sổ hết hạn (ở đây giả lập bằng cách xoá key - chờ 60 giây thật sẽ làm suite chậm vô lý)
     * thì hạn mức được cấp lại nguyên vẹn, và TTL cũng được đặt lại. */
    @Test
    void grantsAFreshQuotaAfterTheWindowExpires() {
        var accountId = UUID.randomUUID();
        IntStream.range(0, ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE)
                .forEach(index -> limiter.checkAndIncrement(accountId));

        redisTemplate.delete(keyOf(accountId));

        assertThatCode(() -> limiter.checkAndIncrement(accountId)).doesNotThrowAnyException();
        assertThat(redisTemplate.getExpire(keyOf(accountId), TimeUnit.SECONDS))
                .isBetween(1L, ChatConstants.RateLimit.WINDOW.toSeconds());
    }
}
