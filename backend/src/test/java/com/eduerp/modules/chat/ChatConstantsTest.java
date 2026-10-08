package com.eduerp.modules.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Bốn con số dưới đây là trần chi phí thật của một tính năng gọi API trả phí (spec Ruling R3 + mục
 * 8). Chốt bằng test để một lần "nới tạm cho dễ test" không âm thầm thành cấu hình production.
 */
class ChatConstantsTest {

    @Test
    void theRateLimitIsTenMessagesPerMinutePerAccount() {
        assertThat(ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE).isEqualTo(10);
        assertThat(ChatConstants.RateLimit.WINDOW).isEqualTo(Duration.ofMinutes(1));
    }

    /** Review Focus #6 + #7: hai trần này nhân với nhau là trần cứng của prompt gửi đi
     * (20 lượt x 2000 ký tự), thứ duy nhất chặn một client gửi một hội thoại dài vô hạn. */
    @Test
    void theMessageAndHistoryLimitsBoundThePromptSize() {
        assertThat(ChatConstants.Limits.MAX_MESSAGE_LENGTH).isEqualTo(2000);
        assertThat(ChatConstants.Limits.MAX_HISTORY_TURNS).isEqualTo(20);
    }

    @Test
    void retrievalIsCappedSoTheGroundingContextStaysSmall() {
        assertThat(ChatConstants.Limits.MAX_RETRIEVED_COURSES).isEqualTo(5);
    }

    /** Namespace Redis do module này sở hữu - key được lắp bằng CacheKeyBuilder, không nối chuỗi tay. */
    @Test
    void theRateLimitNamespaceIsOwnedByThisModule() {
        assertThat(ChatConstants.CacheNamespaces.CHAT_RATE_LIMIT).isEqualTo("chat:rate");
    }
}
