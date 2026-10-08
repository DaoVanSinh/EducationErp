package com.eduerp.modules.chat.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.ai.AiChatClient;
import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.integrations.ai.AiProviderException;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.ChatbotUnavailableException;
import com.eduerp.modules.chat.MessageTooLongException;
import com.eduerp.modules.chat.TooManyMessagesException;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.chat.internal.ChatRateLimiter;
import com.eduerp.modules.courses.CoursesManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Mirror {@code ExportUseCasesTest}: mock thẳng các collaborator ({@code mock(AiChatClient.class)} -
 * class concrete nên Mockito mock được, và cách này đảm bảo KHÔNG test nào gọi Anthropic thật, đúng
 * Global Constraint).
 *
 * <p>Thứ tự gọi là nội dung chính của class này, không phải một chi tiết: Review Focus #1 nói một
 * request bị chặn không được phép tốn một lệnh gọi API trả phí, và cách duy nhất chốt được điều đó là
 * {@code verifyNoInteractions}.
 */
class AskChatbotTest {

    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final AiChatClient aiChatClient = mock(AiChatClient.class);
    private final ChatRateLimiter rateLimiter = mock(ChatRateLimiter.class);
    private final AskChatbot askChatbot = new AskChatbot(courses, aiChatClient, rateLimiter);

    private final UUID actorAccountId = UUID.randomUUID();

    private static CoursesManagement.OpenCourseSummary course(String name) {
        return new CoursesManagement.OpenCourseSummary(UUID.randomUUID(), "C-" + name.hashCode(), name,
                "Mô tả " + name, 24, new BigDecimal("7000000"));
    }

    private String captureSystemText() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(aiChatClient).reply(captor.capture(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<AiChatTurn> captureTurns() {
        var captor = ArgumentCaptor.forClass(List.class);
        verify(aiChatClient).reply(anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void answersWithTheProviderReplyGroundedOnTheRetrievedCourses() {
        when(courses.searchOpenCourses("có khóa tiếng Anh không",
                ChatConstants.Limits.MAX_RETRIEVED_COURSES))
                .thenReturn(List.of(course("Tiếng Anh giao tiếp")));
        when(aiChatClient.reply(anyString(), any())).thenReturn("Trung tâm đang mở khóa Tiếng Anh.");

        var response = askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa tiếng Anh không", List.of()));

        assertThat(response.reply()).isEqualTo("Trung tâm đang mở khóa Tiếng Anh.");
        assertThat(captureSystemText()).contains("Tiếng Anh giao tiếp");
    }

    /** Truy xuất bị giới hạn đúng hằng số của module - không ai được phép truyền một limit tuỳ ý làm
     * phình prompt. */
    @Test
    void asksTheFacadeForAtMostTheConfiguredNumberOfCourses() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("khóa nào", List.of()));

        verify(courses).searchOpenCourses("khóa nào", ChatConstants.Limits.MAX_RETRIEVED_COURSES);
    }

    /**
     * Review Focus #1 - khẳng định quan trọng nhất của cả Phân hệ 5: một request vượt hạn mức KHÔNG
     * được tốn một lệnh gọi API trả phí, và cũng không tốn một câu truy vấn DB.
     */
    @Test
    void checksTheRateLimitBeforeSpendingAnyMoneyOnTheProvider() {
        doThrow(new TooManyMessagesException(actorAccountId))
                .when(rateLimiter).checkAndIncrement(actorAccountId);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa nào không", List.of())))
                .isInstanceOf(TooManyMessagesException.class);

        verifyNoInteractions(aiChatClient);
        verifyNoInteractions(courses);
    }

    /** Review Focus #6: câu hỏi quá dài bị chặn TRƯỚC cả bộ đếm tần suất - một request không hợp lệ
     * không được tiêu một suất trong hạn mức của người gửi. */
    @Test
    void rejectsATooLongMessageBeforeTouchingTheRateLimiterOrTheProvider() {
        var tooLong = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest(tooLong, List.of())))
                .isInstanceOf(MessageTooLongException.class)
                .hasMessageContaining(String.valueOf(ChatConstants.Limits.MAX_MESSAGE_LENGTH));

        verifyNoInteractions(rateLimiter);
        verifyNoInteractions(aiChatClient);
        verifyNoInteractions(courses);
    }

    /** Review Focus #10: câu hỏi rỗng/toàn khoảng trắng cũng bị chặn ở cùng chỗ, cùng errorCode
     * (spec mục 5 gộp hai tình huống). */
    @Test
    void rejectsABlankMessage() {
        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("   \n  ", List.of())))
                .isInstanceOf(MessageTooLongException.class)
                .hasMessageContaining("trống");

        verifyNoInteractions(rateLimiter);
        verifyNoInteractions(aiChatClient);
    }

    /**
     * Review Focus #2: truy xuất rỗng KHÔNG phải một lỗi và cũng không được thành một system text
     * rỗng - nó phải là một câu khẳng định rõ ràng, vì một system message chỉ có luật mà không có dữ
     * liệu là đúng điều kiện khiến model tự bịa ra khóa học.
     */
    @Test
    void stillSendsANonBlankSystemTextWhenRetrievalFoundNothing() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("Chưa tìm thấy khóa phù hợp.");

        var response = askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa lập trình nhúng không", List.of()));

        assertThat(response.reply()).isEqualTo("Chưa tìm thấy khóa phù hợp.");
        assertThat(captureSystemText()).isNotBlank().contains("Không tìm thấy khóa học nào");
    }

    /** Luật chống bịa đặt phải có trong MỌI lệnh gọi, kể cả khi truy xuất có kết quả. */
    @Test
    void alwaysSendsTheAntiHallucinationRule() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of(course("Toán 9")));
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("toán 9 học phí bao nhiêu", List.of()));

        assertThat(captureSystemText()).contains("CHỈ trả lời dựa trên danh sách", "không bịa");
    }

    @Test
    void sendsTheConversationHistoryFollowedByTheNewQuestion() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("học phí bao nhiêu", List.of(
                new ChatTurnPayload(ChatConstants.TurnRole.USER, "có khóa toán không"),
                new ChatTurnPayload(ChatConstants.TurnRole.ASSISTANT, "Có khóa Toán 9."))));

        assertThat(captureTurns()).containsExactly(
                new AiChatTurn(AiChatTurn.Role.USER, "có khóa toán không"),
                new AiChatTurn(AiChatTurn.Role.ASSISTANT, "Có khóa Toán 9."),
                new AiChatTurn(AiChatTurn.Role.USER, "học phí bao nhiêu"));
    }

    /** Review Focus #7: một client gửi 500 lượt lịch sử trong một request không được biến thành một
     * prompt 500 lượt - rate limit đếm số REQUEST nên nó không thấy chuyện này. */
    @Test
    void truncatesAnAbsurdlyLongHistoryBeforeCallingTheProvider() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");
        // Alternating USER/ASSISTANT: hình dạng một hội thoại thật. Final review I1: một fixture toàn
        // USER (trước đây) đã che mất lỗi alternation mà ChatPromptRules.repairAlternation sửa.
        var huge = IntStream.range(0, 500)
                .mapToObj(index -> new ChatTurnPayload(
                        index % 2 == 0 ? ChatConstants.TurnRole.USER : ChatConstants.TurnRole.ASSISTANT,
                        "câu " + index))
                .toList();

        askChatbot.execute(actorAccountId, new AskChatbotRequest("câu mới", huge));

        assertThat(captureTurns()).hasSize(ChatConstants.Limits.MAX_HISTORY_TURNS + 1);
    }

    /** Lượt hỏi đầu tiên của phiên: history vắng mặt trong JSON là hợp lệ, không phải NullPointer. */
    @Test
    void acceptsARequestWithoutAnyHistoryField() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("câu đầu tiên", null));

        assertThat(captureTurns())
                .containsExactly(new AiChatTurn(AiChatTurn.Role.USER, "câu đầu tiên"));
    }

    /**
     * Review Focus #5: lỗi hạ tầng của nhà cung cấp phải thành MỘT lỗi nghiệp vụ 503 có errorCode.
     * Nguyên nhân gốc phải được giữ lại, nếu không không ai debug được từ log.
     */
    @Test
    void translatesAiProviderExceptionIntoABusinessFailure() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        var providerFailure = new AiProviderException("401 authentication_error", null);
        when(aiChatClient.reply(anyString(), any())).thenThrow(providerFailure);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa nào không", List.of())))
                .isInstanceOf(ChatbotUnavailableException.class)
                .hasCause(providerFailure);
    }

    /** Lỗi của bộ đếm tần suất (Redis sập - đã là ChatbotUnavailableException) phải nổi nguyên vẹn,
     * không bị nhánh catch của provider bọc thêm một lớp. */
    @Test
    void propagatesARateLimiterFailureUnchanged() {
        var redisFailure = new ChatbotUnavailableException("không đo được hạn mức tần suất", null);
        doThrow(redisFailure).when(rateLimiter).checkAndIncrement(actorAccountId);

        assertThatThrownBy(() -> askChatbot.execute(actorAccountId,
                new AskChatbotRequest("có khóa nào không", List.of())))
                .isSameAs(redisFailure);

        verify(aiChatClient, never()).reply(anyString(), any());
    }

    /** Hạn mức đếm theo ĐÚNG tài khoản gọi, không theo một id nào khác. */
    @Test
    void countsTheQuotaAgainstTheCallingAccount() {
        when(courses.searchOpenCourses(anyString(), anyInt())).thenReturn(List.of());
        when(aiChatClient.reply(anyString(), any())).thenReturn("ok");

        askChatbot.execute(actorAccountId, new AskChatbotRequest("câu hỏi", List.of()));

        verify(rateLimiter).checkAndIncrement(eq(actorAccountId));
    }
}
