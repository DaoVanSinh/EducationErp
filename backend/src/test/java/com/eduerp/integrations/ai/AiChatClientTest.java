package com.eduerp.integrations.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * Giới hạn đã nêu ở spec Ruling R2: KHÔNG có cách nào test tự động một lệnh gọi Anthropic THẬT trong
 * phiên này (chưa có ANTHROPIC_API_KEY), nên class này chỉ test đúng hai thứ test được mà không cần
 * khoá: hình dạng prompt gửi đi, và việc dịch mọi thất bại của provider thành MỘT kiểu lỗi mà
 * modules.chat biết bắt. Đó không phải một lỗ hổng test bị bỏ sót.
 *
 * <p>Chuỗi fluent của Spring AI được mock ở đúng ba mức vì {@code AiChatClient} cố ý dùng overload
 * {@code prompt(Prompt)} thay vì {@code prompt().system(...).messages(...)}: một Prompt dựng sẵn vừa
 * khiến việc lắp message trở thành một hàm thuần kiểm được bằng captor, vừa tránh thứ tự message bị
 * trộn giữa {@code system()}/{@code user()} và {@code messages()} (một lỗi đã biết của ChatClient).
 */
class AiChatClientTest {

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    private final AiChatClient client = new AiChatClient(chatClient);

    @BeforeEach
    void wireTheFluentChain() {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
    }

    private Prompt capturePrompt() {
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(captor.capture());
        return captor.getValue();
    }

    @Test
    void returnsTheProviderAnswer() {
        when(responseSpec.content()).thenReturn("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");

        assertThat(client.reply("ngữ cảnh", List.of(new AiChatTurn(AiChatTurn.Role.USER, "có khóa nào không"))))
                .isEqualTo("Trung tâm đang mở khóa Tiếng Anh giao tiếp buổi tối.");
    }

    /** System message phải đứng ĐẦU và mang nguyên văn groundingContext mà nơi gọi dựng - nếu nó bị
     * đẩy xuống sau lịch sử, luật chống bịa đặt mất hiệu lực với phần hội thoại trước đó. */
    @Test
    void putsTheGroundingContextIntoTheFirstSystemMessage() {
        when(responseSpec.content()).thenReturn("ok");

        client.reply("CHỈ trả lời dựa trên danh sách sau: Toán 9", List.of());

        var messages = capturePrompt().getInstructions();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("CHỈ trả lời dựa trên danh sách sau: Toán 9");
    }

    /** Hội thoại nhiều lượt: thứ tự và vai của từng lượt phải giữ nguyên, nếu không model đọc câu trả
     * lời của chính nó như là câu hỏi của học viên. */
    @Test
    void mapsEveryHistoryTurnToTheMatchingSpringAiMessageTypeInOrder() {
        when(responseSpec.content()).thenReturn("ok");

        client.reply("ngữ cảnh", List.of(
                new AiChatTurn(AiChatTurn.Role.USER, "có khóa toán không"),
                new AiChatTurn(AiChatTurn.Role.ASSISTANT, "Có khóa Toán 9 nâng cao."),
                new AiChatTurn(AiChatTurn.Role.USER, "học phí bao nhiêu")));

        var messages = capturePrompt().getInstructions();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("có khóa toán không");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("Có khóa Toán 9 nâng cao.");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo("học phí bao nhiêu");
    }

    /**
     * Review Focus #5: một lỗi 401 vì thiếu khoá (tình huống MẶC ĐỊNH của repo hiện tại) phải ra một
     * kiểu lỗi mà modules.chat biết bắt, không phải một NonTransientAiException lọt ra tới
     * GlobalExceptionHandler thành 500 trống.
     */
    @Test
    void translatesAProviderFailureIntoAiProviderException() {
        when(responseSpec.content())
                .thenThrow(new NonTransientAiException("401 - {\"error\":{\"type\":\"authentication_error\"}}"));

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasCauseInstanceOf(NonTransientAiException.class);
    }

    /** Review Focus #9: content() khai báo trả String nhưng trả null được (response không có
     * generation nào). Trả null xuống frontend sẽ làm Zod ném một lỗi parse vô nghĩa thay cho một 503
     * rõ ràng. */
    @Test
    void treatsANullAnswerAsAProviderFailure() {
        when(responseSpec.content()).thenReturn(null);

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void treatsAnEmptyAnswerAsAProviderFailure() {
        when(responseSpec.content()).thenReturn("   ");

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class);
    }

    /**
     * Lỗi "câu trả lời rỗng" do chính class này phát hiện là lỗi GỐC, không được bọc thêm một lớp
     * AiProviderException nữa - nơi gọi đọc {@code getCause()} để log nguyên nhân thật, và một cause
     * trỏ về chính kiểu của nó là một log vô nghĩa.
     */
    @Test
    void doesNotWrapAnAiProviderExceptionTwice() {
        when(responseSpec.content()).thenReturn("");

        assertThatThrownBy(() -> client.reply("ngữ cảnh", List.of()))
                .isInstanceOf(AiProviderException.class)
                .satisfies(thrown -> assertThat(thrown.getCause()).isNull());
    }
}
