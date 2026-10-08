package com.eduerp.modules.chat.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.chat.ChatbotUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Review Focus #8 - quyết định mà spec không nói: Redis sập đúng lúc kiểm rate limit thì FAIL CLOSED.
 *
 * <p>Lý do: cả mục đích tồn tại của bộ đếm này là chặn một hoá đơn API bất ngờ (spec mục 2), nên "đo
 * không được thì cứ gọi" chính là tình huống nó sinh ra để ngăn. Mất tính năng chat vài phút rẻ hơn
 * một hoá đơn không giới hạn - và thực tế khi Redis sập thì {@code PermissionCacheService} cũng sập
 * theo nên gần như không ai đi tới được bước này. 503 có errorCode vẫn đúng tinh thần "không bao giờ
 * 500 trống".
 */
class ChatRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ChatRateLimiter limiter = new ChatRateLimiter(redis);

    @Test
    void failsClosedWhenRedisIsUnreachable() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> limiter.checkAndIncrement(UUID.randomUUID()))
                .isInstanceOf(ChatbotUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    /** Lỗi xảy ra ở lệnh INCR (không phải lúc lấy opsForValue) cũng phải fail closed như nhau. */
    @Test
    void failsClosedWhenTheIncrementItselfFails() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> limiter.checkAndIncrement(UUID.randomUUID()))
                .isInstanceOf(ChatbotUnavailableException.class);
    }
}
