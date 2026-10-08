package com.eduerp.modules.chat.usecase;

import com.eduerp.integrations.ai.AiChatClient;
import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.integrations.ai.AiProviderException;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.ChatbotUnavailableException;
import com.eduerp.modules.chat.MessageTooLongException;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.AskChatbotResponse;
import com.eduerp.modules.chat.internal.ChatRateLimiter;
import com.eduerp.modules.chat.internal.rules.ChatPromptRules;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Hỏi chatbot tư vấn khóa học (spec mục 3.2). Năm bước, và THỨ TỰ là nội dung nghiệp vụ chứ không
 * phải một chi tiết cài đặt:
 *
 * <ol>
 *   <li>Kiểm độ dài câu hỏi - chặn một đoạn văn khổng lồ làm phình prompt (Review Focus #6). Kiểm
 *       TRƯỚC bộ đếm tần suất: một request không hợp lệ không được tiêu một suất trong hạn mức của
 *       người gửi.</li>
 *   <li>{@code ChatRateLimiter.checkAndIncrement} - TRƯỚC khi gọi LLM, vì "một request bị chặn không
 *       được phép vẫn tốn một lệnh gọi API trả phí" (Review Focus #1).</li>
 *   <li>{@code CoursesManagement.searchOpenCourses} - truy xuất khóa học ĐANG MỞ qua facade.</li>
 *   <li>{@code ChatPromptRules.buildSystemText} - hàm thuần, dựng luật chống bịa đặt + danh sách.</li>
 *   <li>{@code AiChatClient.reply} - lệnh gọi duy nhất tốn tiền trong cả luồng.</li>
 * </ol>
 *
 * <p><b>Cố ý KHÔNG mang {@code @Transactional}</b>, khác mọi usecase khác trong repo. Đây là bài học
 * đã ghi thẳng trong javadoc của {@code PaymentHttpClientConfig}: một lệnh gọi HTTP ra ngoài nằm
 * trong một usecase {@code @Transactional} giữ một connection HikariCP suốt thời gian chờ mạng. Ở
 * đây thời gian chờ là 15-60 giây (một model chat), nên bọc transaction quanh nó là cách nhanh nhất
 * rút cạn connection pool của toàn app bằng vài chục câu hỏi đồng thời. Usecase này không ghi gì, và
 * {@code searchOpenCourses} đã tự có {@code @Transactional(readOnly = true)} cho đúng phần cần DB.
 */
@Service
public class AskChatbot {

    private final CoursesManagement courses;
    private final AiChatClient aiChatClient;
    private final ChatRateLimiter rateLimiter;

    AskChatbot(CoursesManagement courses, AiChatClient aiChatClient, ChatRateLimiter rateLimiter) {
        this.courses = courses;
        this.aiChatClient = aiChatClient;
        this.rateLimiter = rateLimiter;
    }

    public AskChatbotResponse execute(UUID actorAccountId, AskChatbotRequest request) {
        var message = request.message();
        if (!ChatPromptRules.isMessageAcceptable(message)) {
            throw new MessageTooLongException(message.strip().length());
        }
        rateLimiter.checkAndIncrement(actorAccountId);
        var matched = courses.searchOpenCourses(message.strip(),
                ChatConstants.Limits.MAX_RETRIEVED_COURSES);
        var systemText = ChatPromptRules.buildSystemText(matched);
        var turns = ChatPromptRules.toProviderTurns(request.history(), message);
        return new AskChatbotResponse(reply(systemText, turns));
    }

    private String reply(String systemText, List<AiChatTurn> turns) {
        try {
            return aiChatClient.reply(systemText, turns);
        } catch (AiProviderException providerFailure) {
            // Review Focus #5 + bài học Critical #1 của Phân hệ 4: không bao giờ để một lỗi hạ tầng
            // rò ra thành 500 trống. Bắt HẸP đúng AiProviderException - một ChatbotUnavailableException
            // đến từ ChatRateLimiter (Redis sập) phải nổi nguyên vẹn, không bị bọc hai lần.
            throw new ChatbotUnavailableException("lỗi từ nhà cung cấp mô hình", providerFailure);
        }
    }
}
