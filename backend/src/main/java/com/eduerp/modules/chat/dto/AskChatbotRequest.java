package com.eduerp.modules.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code message} cố ý KHÔNG mang {@code @Size}: độ dài được kiểm trong usecase để lỗi mang đúng
 * errorCode {@code CHAT_MESSAGE_TOO_LONG} của bảng lỗi (spec mục 5) thay vì
 * {@code VALIDATION_FAILED} - xem javadoc {@code MessageTooLongException}.
 *
 * <p>{@code history} vắng mặt trong JSON (lượt hỏi đầu tiên của phiên) là hợp lệ và có nghĩa "chưa
 * có lịch sử", nên compact constructor chuẩn hoá nó về danh sách rỗng: mọi nơi dùng về sau không
 * phải kiểm null, và một {@code List.copyOf} cũng khoá luôn tính bất biến của hợp đồng.
 */
public record AskChatbotRequest(@NotNull String message, @Valid List<ChatTurnPayload> history) {

    public AskChatbotRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
