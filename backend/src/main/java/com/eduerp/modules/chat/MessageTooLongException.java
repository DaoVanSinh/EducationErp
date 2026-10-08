package com.eduerp.modules.chat;

import org.springframework.http.HttpStatus;

/**
 * Review Focus #6. Spec mục 5 gộp "tin nhắn rỗng" và "tin nhắn quá dài" vào cùng một errorCode
 * {@code CHAT_MESSAGE_TOO_LONG} - giữ đúng hợp đồng đó, nhưng thông báo phải nói đúng chuyện đang xảy
 * ra ("câu hỏi dài 0 ký tự, vượt giới hạn 2000" là một câu vô nghĩa với người đọc).
 *
 * <p>Vì sao không dùng {@code @Size} trên {@code AskChatbotRequest.message}: annotation sẽ cho
 * errorCode {@code VALIDATION_FAILED}, lệch khỏi bảng lỗi của spec và khiến frontend phải xử lý hai
 * hình dạng lỗi cho cùng một tình huống.
 */
public final class MessageTooLongException extends ChatException {

    public MessageTooLongException(int length) {
        super("CHAT_MESSAGE_TOO_LONG", HttpStatus.BAD_REQUEST, describe(length));
    }

    private static String describe(int length) {
        if (length == 0) {
            return "Câu hỏi đang để trống - hãy nhập câu hỏi về khóa học bạn quan tâm";
        }
        return "Câu hỏi dài " + length + " ký tự, vượt giới hạn "
                + ChatConstants.Limits.MAX_MESSAGE_LENGTH + " ký tự";
    }
}
