package com.eduerp.modules.chat;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #1: 429, không phải 403. Người gửi KHÔNG thiếu quyền - họ chỉ cần chờ hết cửa sổ một
 * phút. Trả 403 ở đây sẽ làm frontend hiển thị "bạn không có quyền", một câu sai.
 */
public final class TooManyMessagesException extends ChatException {

    public TooManyMessagesException(UUID accountId) {
        super("CHAT_TOO_MANY_MESSAGES", HttpStatus.TOO_MANY_REQUESTS,
                "Tài khoản " + accountId + " đã gửi quá " + ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE
                        + " câu hỏi trong một phút, vui lòng chờ một lát rồi thử lại");
    }
}
