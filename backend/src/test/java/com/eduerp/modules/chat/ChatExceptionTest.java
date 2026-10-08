package com.eduerp.modules.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Mirror {@code AcademicExceptionTest}: errorCode là hợp đồng với frontend (ApiError.errorCode), đổi
 * là breaking. Ba errorCode dưới đây copy nguyên văn bảng lỗi của spec mục 5. */
class ChatExceptionTest {

    /** Review Focus #6: câu hỏi quá dài bị chặn ở backend, và thông báo phải nói ra giới hạn thật để
     * người gửi biết cần cắt bao nhiêu. */
    @Test
    void messageTooLongIsBadRequestAndNamesTheLimit() {
        var ex = new MessageTooLongException(5000);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_MESSAGE_TOO_LONG");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage())
                .contains("5000", String.valueOf(ChatConstants.Limits.MAX_MESSAGE_LENGTH));
    }

    /** Spec mục 5 gộp "rỗng" và "quá dài" vào cùng một errorCode, nhưng thông báo phải nói đúng
     * chuyện đang xảy ra - "câu hỏi dài 0 ký tự vượt giới hạn 2000" là một câu vô nghĩa. */
    @Test
    void messageTooLongSaysTheQuestionIsEmptyWhenTheLengthIsZero() {
        var ex = new MessageTooLongException(0);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_MESSAGE_TOO_LONG");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("trống").doesNotContain("0 ký tự");
    }

    /** Review Focus #1: 429 chứ không 403 - người gửi KHÔNG thiếu quyền, họ chỉ cần chờ. */
    @Test
    void tooManyMessagesIsTooManyRequestsAndNamesTheQuota() {
        var accountId = UUID.randomUUID();
        var ex = new TooManyMessagesException(accountId);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_TOO_MANY_MESSAGES");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).contains(accountId.toString(),
                String.valueOf(ChatConstants.Limits.MAX_MESSAGES_PER_MINUTE));
    }

    /** Review Focus #5: 503 có errorCode, không phải 500 trống. Nguyên nhân gốc phải được giữ lại -
     * không có nó thì không ai debug được "vì sao chatbot chết" từ log. */
    @Test
    void chatbotUnavailableIsServiceUnavailableAndKeepsTheRootCause() {
        var cause = new IllegalStateException("401 authentication_error");
        var ex = new ChatbotUnavailableException("lỗi từ nhà cung cấp mô hình", cause);

        assertThat(ex.getErrorCode()).isEqualTo("CHAT_PROVIDER_UNAVAILABLE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getMessage()).contains("lỗi từ nhà cung cấp mô hình");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    /** Một nguyên nhân null (lỗi do chính ta phát hiện, vd câu trả lời rỗng) không được làm vỡ
     * constructor. */
    @Test
    void chatbotUnavailableAcceptsAMissingCause() {
        var ex = new ChatbotUnavailableException("câu trả lời rỗng", null);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getCause()).isNull();
    }
}
