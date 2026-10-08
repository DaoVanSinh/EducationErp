package com.eduerp.integrations.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Nơi DUY NHẤT trong toàn app gọi một mô hình ngôn ngữ.
 *
 * <p>Không streaming (spec Ruling R4): một lệnh gọi, một câu trả lời đầy đủ.
 *
 * <p>Không có khoá thật thì lệnh gọi sẽ 401 và ném {@link AiProviderException} - đó là hành vi đúng
 * và đã được tính tới (spec Ruling R2 + bảng lỗi mục 5), không phải một lỗi cần vá. Mọi test chạm
 * tới class này dùng {@link #AiChatClient(ChatClient)} với một {@code ChatClient} giả lập; không test
 * nào gọi mạng thật.
 */
@Service
public class AiChatClient {

    private final ChatClient chatClient;

    @Autowired
    AiChatClient(ChatModel chatModel) {
        this(ChatClient.create(chatModel));
    }

    /** Constructor cho test: chuỗi fluent của Spring AI chỉ giả lập được khi ChatClient tiêm vào được
     * - mirror constructor-cho-test của {@code VnPayPaymentGatewayClient}. */
    AiChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * @param groundingContext TOÀN BỘ nội dung system message, do nơi gọi dựng (luật chống bịa đặt +
     *     danh sách khóa học truy xuất được). Class này không thêm một chữ nghiệp vụ nào vào đó.
     * @param history mọi lượt hỏi/đáp của phiên, lượt cuối là câu hỏi mới. Không được lưu ở đâu.
     * @throws AiProviderException mọi thất bại của nhà cung cấp, gồm cả câu trả lời rỗng
     */
    public String reply(String groundingContext, List<AiChatTurn> history) {
        String answer = callProvider(groundingContext, history);
        if (answer == null || answer.isBlank()) {
            // Review Focus #9: content() trả null được. Trả null lên trên sẽ thành {"reply": null},
            // và Zod ở frontend ném một lỗi parse không ai hiểu thay cho một 503 rõ ràng.
            throw new AiProviderException("Nhà cung cấp trả về câu trả lời rỗng", null);
        }
        return answer;
    }

    private String callProvider(String groundingContext, List<AiChatTurn> history) {
        try {
            return chatClient.prompt(toPrompt(groundingContext, history)).call().content();
        } catch (RuntimeException providerFailure) {
            // Bắt rộng là có chủ đích: Spring AI ném NonTransientAiException/TransientAiException
            // (org.springframework.ai.retry) cho lỗi HTTP, RestClientException cho lỗi mạng, và
            // IllegalArgumentException/IllegalStateException cho cấu hình sai - ba thư viện, ba cây
            // exception, và danh sách đó đổi được giữa hai bản patch của Spring AI. Với nơi gọi thì
            // cả ba đều là đúng một sự thật: "nhà cung cấp không trả lời được". Để một kiểu lạ lọt
            // ra ngoài là để nó thành 500 trống - đúng điều Review Focus #5 cấm.
            throw new AiProviderException("Không gọi được nhà cung cấp mô hình ngôn ngữ", providerFailure);
        }
    }

    /**
     * Hàm thuần, package-private để test khẳng định được thứ tự/vai của từng message mà không cần
     * dựng cả service. Dùng overload {@code prompt(Prompt)} thay vì
     * {@code prompt().system(...).messages(...)}: ChatClient xếp list của {@code messages()} TRƯỚC
     * system/user của {@code system()}/{@code user()}, nên cách fluent sẽ đẩy system message xuống
     * sau lịch sử.
     */
    static Prompt toPrompt(String groundingContext, List<AiChatTurn> history) {
        List<Message> messages = new ArrayList<>(history.size() + 1);
        messages.add(new SystemMessage(groundingContext));
        history.forEach(turn -> messages.add(toMessage(turn)));
        return new Prompt(messages);
    }

    private static Message toMessage(AiChatTurn turn) {
        return turn.role() == AiChatTurn.Role.ASSISTANT
                ? new AssistantMessage(turn.content())
                : new UserMessage(turn.content());
    }
}
