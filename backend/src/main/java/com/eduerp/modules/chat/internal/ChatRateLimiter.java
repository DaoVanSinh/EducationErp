package com.eduerp.modules.chat.internal;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.ChatbotUnavailableException;
import com.eduerp.modules.chat.TooManyMessagesException;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Hạn mức tần suất theo tài khoản, cửa sổ một phút, đếm bằng Redis INCR + EXPIRE - mirror cách
 * {@code PermissionCacheService} tiêm {@code StringRedisTemplate} (dependency
 * {@code spring-boot-starter-data-redis} đã có sẵn).
 *
 * <p>Vì sao Redis chứ không một bộ đếm trong bộ nhớ: app chạy nhiều instance được, và một bộ đếm
 * trong bộ nhớ sẽ cho mỗi instance một hạn mức riêng - tức hạn mức thật là 10 nhân số instance,
 * đúng thứ mà một biện pháp chặn chi phí không được phép mắc.
 *
 * <p>Cửa sổ là "fixed window" đơn giản, không sliding: một học viên có thể gửi 10 câu ở giây 59 và 10
 * câu nữa ở giây 61. Chấp nhận được - đây là chặn lạm dụng/sự cố, không phải chống tấn công có tổ
 * chức, và một sliding window đòi một sorted set cùng việc dọn rác không đáng cho việc này.
 */
@Component
public class ChatRateLimiter {

    private final StringRedisTemplate redis;

    ChatRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @throws TooManyMessagesException đã vượt {@code MAX_MESSAGES_PER_MINUTE} trong cửa sổ hiện tại
     * @throws ChatbotUnavailableException Redis không đọc được - fail CLOSED, xem javadoc
     *     {@code ChatRateLimiterTest}
     */
    public void checkAndIncrement(UUID accountId) {
        if (increment(accountId) > ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE) {
            throw new TooManyMessagesException(accountId);
        }
    }

    private long increment(UUID accountId) {
        var key = CacheKeyBuilder.key(ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT, accountId);
        try {
            Long used = redis.opsForValue().increment(key);
            if (used != null && used == 1L) {
                // Đặt TTL ĐÚNG MỘT LẦN, ở lượt đầu của cửa sổ. Đặt lại ở mọi lượt sẽ biến cửa sổ
                // thành "một phút kể từ câu hỏi CUỐI", nghĩa là một người hỏi liên tục không bao giờ
                // được cấp lại hạn mức.
                redis.expire(key, ChatConstants.RateLimit.WINDOW);
            }
            // increment() chỉ trả null khi đang trong pipeline/transaction - không phải ở đây. Coi nó
            // là lượt đầu tiên thay vì ném NPE: một bộ đếm không đọc được giá trị thì chặn bằng
            // nhánh catch bên dưới, không bằng một NullPointerException giữa luồng.
            return used == null ? 1L : used;
        } catch (RuntimeException redisUnavailable) {
            throw new ChatbotUnavailableException("không đo được hạn mức tần suất", redisUnavailable);
        }
    }
}
