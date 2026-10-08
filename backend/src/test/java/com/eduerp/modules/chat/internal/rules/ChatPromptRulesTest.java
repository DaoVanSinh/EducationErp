package com.eduerp.modules.chat.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.courses.CoursesManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code GradeRulesTest}. */
class ChatPromptRulesTest {

    private static CoursesManagement.OpenCourseSummary course(String name, String description,
            Integer sessions, String fee) {
        return new CoursesManagement.OpenCourseSummary(UUID.randomUUID(), "C-" + name.hashCode(), name,
                description, sessions, fee == null ? null : new BigDecimal(fee));
    }

    @Test
    void listsEveryRetrievedCourseWithItsNameSessionsAndFee() {
        var context = ChatPromptRules.buildGroundingContext(List.of(
                course("Tiếng Anh giao tiếp", "Buổi tối thứ 2-4", 24, "7000000"),
                course("Toán 9 nâng cao", "Ôn thi vào 10", 36, "9000000")));

        assertThat(context).contains("Tiếng Anh giao tiếp", "Buổi tối thứ 2-4", "24", "7000000",
                "Toán 9 nâng cao", "Ôn thi vào 10", "36", "9000000");
    }

    /** Review Focus #2: "không tìm thấy khóa phù hợp" phải là một câu CÓ NGHĨA, không phải chuỗi rỗng
     * - chuỗi rỗng dễ khiến LLM tự suy diễn ra khóa học không tồn tại. */
    @Test
    void returnsAMeaningfulSentenceWhenNothingMatched() {
        var context = ChatPromptRules.buildGroundingContext(List.of());

        assertThat(context).contains("Không tìm thấy khóa học nào");
    }

    @Test
    void neverReturnsABlankGroundingContext() {
        assertThat(ChatPromptRules.buildGroundingContext(List.of())).isNotBlank();
        assertThat(ChatPromptRules.buildGroundingContext(List.of(course("X", null, null, null))))
                .isNotBlank();
    }

    /** Spec mục 6: "không bao giờ in null". Khóa chưa chốt giá/chưa cấu hình số buổi/chưa có mô tả là
     * ba trạng thái hợp lệ của dữ liệu thật, và cả ba phải thành một câu tiếng Việt - "học phí null"
     * là đúng cái loại rác khiến LLM bịa ra một con số. */
    @Test
    void neverPrintsTheWordNullForACourseWithMissingFields() {
        var context = ChatPromptRules.buildGroundingContext(List.of(course("Thư pháp", null, null, null)));

        assertThat(context).doesNotContain("null")
                .contains("Thư pháp", "chưa có mô tả", "chưa cấu hình tổng số buổi",
                        "chưa công bố học phí");
    }

    /** Mô tả chỉ gồm khoảng trắng cũng phải bị coi là "chưa có mô tả", không in ra một dòng trống. */
    @Test
    void treatsABlankDescriptionAsMissing() {
        assertThat(ChatPromptRules.buildGroundingContext(List.of(course("Vẽ", "   ", 12, "1000000"))))
                .contains("chưa có mô tả");
    }

    /** Global Constraint: luật chống bịa đặt là bắt buộc, không tắt được - nên nó phải có mặt trong
     * MỌI system text, kể cả khi truy xuất rỗng. */
    @Test
    void alwaysPutsTheAntiHallucinationRuleAheadOfTheCourseList() {
        var withCourses = ChatPromptRules.buildSystemText(List.of(course("Toán 9", "Mô tả", 24, "1")));
        var withoutCourses = ChatPromptRules.buildSystemText(List.of());

        assertThat(withCourses).startsWith(ChatPromptRules.SYSTEM_PROMPT).contains("Toán 9");
        assertThat(withoutCourses).startsWith(ChatPromptRules.SYSTEM_PROMPT);
    }

    /** Bốn mệnh lệnh của system prompt (spec mục 3.2) đều phải có mặt - xoá một trong bốn là mở đường
     * cho chatbot bịa khóa học, bịa học phí, hoặc trả lời bằng tiếng Anh. */
    @Test
    void theSystemPromptCarriesAllFourRulesTheSpecDictates() {
        assertThat(ChatPromptRules.SYSTEM_PROMPT)
                .contains("CHỈ trả lời dựa trên danh sách")
                .contains("không bịa")
                .contains("chưa tìm thấy")
                .contains("tiếng Việt");
    }

    @Test
    void appendsTheNewQuestionAsTheLastUserTurn() {
        var turns = ChatPromptRules.toProviderTurns(List.of(
                new ChatTurnPayload(ChatConstants.TurnRole.USER, "có khóa toán không"),
                new ChatTurnPayload(ChatConstants.TurnRole.ASSISTANT, "Có khóa Toán 9.")),
                "học phí bao nhiêu");

        assertThat(turns).hasSize(3);
        assertThat(turns.get(0)).isEqualTo(new AiChatTurn(AiChatTurn.Role.USER, "có khóa toán không"));
        assertThat(turns.get(1)).isEqualTo(new AiChatTurn(AiChatTurn.Role.ASSISTANT, "Có khóa Toán 9."));
        assertThat(turns.get(2)).isEqualTo(new AiChatTurn(AiChatTurn.Role.USER, "học phí bao nhiêu"));
    }

    @Test
    void sendsOnlyTheNewQuestionWhenThereIsNoHistoryYet() {
        var turns = ChatPromptRules.toProviderTurns(List.of(), "có khóa nào buổi tối không");

        assertThat(turns).containsExactly(
                new AiChatTurn(AiChatTurn.Role.USER, "có khóa nào buổi tối không"));
    }

    /**
     * Review Focus #7: lịch sử do client gửi lên không bị chặn bởi bất cứ gì ở client. Chỉ
     * MAX_HISTORY_TURNS lượt GẦN NHẤT được gửi - giữ lượt gần nhất (không phải lượt đầu) vì đó là
     * phần hội thoại câu hỏi hiện tại thực sự dựa vào.
     */
    @Test
    void keepsOnlyTheMostRecentTurnsWhenTheHistoryIsAbsurdlyLong() {
        var huge = IntStream.range(0, 500)
                .mapToObj(index -> new ChatTurnPayload(ChatConstants.TurnRole.USER, "câu " + index))
                .toList();

        var turns = ChatPromptRules.toProviderTurns(huge, "câu cuối");

        assertThat(turns).hasSize(ChatConstants.Limits.MAX_HISTORY_TURNS + 1);
        assertThat(turns.get(0).content())
                .as("lượt đầu được giữ phải là lượt thứ 480, không phải lượt 0")
                .isEqualTo("câu " + (500 - ChatConstants.Limits.MAX_HISTORY_TURNS));
        assertThat(turns.get(turns.size() - 1).content()).isEqualTo("câu cuối");
    }

    /** Đúng ngưỡng thì không cắt gì - một lỗi off-by-one ở đây sẽ âm thầm bỏ mất lượt cũ nhất của mọi
     * hội thoại dài đúng 20 lượt. */
    @Test
    void keepsTheWholeHistoryWhenItIsExactlyAtTheLimit() {
        var exact = IntStream.range(0, ChatConstants.Limits.MAX_HISTORY_TURNS)
                .mapToObj(index -> new ChatTurnPayload(ChatConstants.TurnRole.USER, "câu " + index))
                .toList();

        var turns = ChatPromptRules.toProviderTurns(exact, "mới");

        assertThat(turns).hasSize(ChatConstants.Limits.MAX_HISTORY_TURNS + 1);
        assertThat(turns.get(0).content()).isEqualTo("câu 0");
    }

    /** Review Focus #6: trần độ dài phải được quyết định ở đây, nơi backend kiểm được, không phải ở
     * maxLength của một ô input. */
    @Test
    void rejectsAMessageLongerThanTheLimit() {
        var tooLong = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH + 1);

        assertThat(ChatPromptRules.isMessageAcceptable(tooLong)).isFalse();
    }

    @Test
    void acceptsAMessageExactlyAtTheLimit() {
        var exact = "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH);

        assertThat(ChatPromptRules.isMessageAcceptable(exact)).isTrue();
    }

    /** Review Focus #10: một câu hỏi toàn khoảng trắng không được tiêu một lệnh gọi LLM nào. */
    @Test
    void rejectsAMessageThatIsOnlyWhitespace() {
        assertThat(ChatPromptRules.isMessageAcceptable("   \n\t  ")).isFalse();
        assertThat(ChatPromptRules.isMessageAcceptable("")).isFalse();
    }

    /** Khoảng trắng hai đầu không được tính vào trần độ dài - người dán văn bản thường kéo theo
     * khoảng trắng, và bị 400 vì một dấu cách là một lỗi vô lý. */
    @Test
    void ignoresSurroundingWhitespaceWhenMeasuringTheLength() {
        var padded = "  " + "a".repeat(ChatConstants.Limits.MAX_MESSAGE_LENGTH) + "  ";

        assertThat(ChatPromptRules.isMessageAcceptable(padded)).isTrue();
    }

    @Test
    void acceptsANormalQuestion() {
        assertThat(ChatPromptRules.isMessageAcceptable("có khóa tiếng Anh buổi tối không")).isTrue();
    }
}
