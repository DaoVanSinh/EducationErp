package com.eduerp.modules.chat;

import org.springframework.http.HttpStatus;

/**
 * Review Focus #5, và là bài học trực tiếp từ Critical #1 của Phân hệ 4: một lỗi hạ tầng không bao
 * giờ được rò ra thành 503/500 không có errorCode. Hai nguồn đổ về đây:
 * {@code AiProviderException} của {@code integrations.ai} (timeout, 401 vì chưa cấu hình khoá, 429
 * của chính Anthropic, câu trả lời rỗng) và một Redis không đọc được lúc kiểm rate limit
 * (xem {@code ChatRateLimiter} - fail closed).
 */
public final class ChatbotUnavailableException extends ChatException {

    public ChatbotUnavailableException(String reason, Throwable cause) {
        super("CHAT_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Trợ lý tư vấn tạm thời không trả lời được (" + reason + "), vui lòng thử lại sau");
        // AppException không có constructor nhận cause, nhưng mất nguyên nhân gốc là mất khả năng
        // debug "vì sao chatbot chết" từ log. Throwable(String) để cause ở trạng thái CHƯA khởi tạo
        // (cause == this), nên initCause hợp lệ đúng một lần ở đây.
        initCause(cause);
    }
}
