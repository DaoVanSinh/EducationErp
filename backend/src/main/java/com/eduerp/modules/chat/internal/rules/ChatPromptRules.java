package com.eduerp.modules.chat.internal.rules;

import com.eduerp.integrations.ai.AiChatTurn;
import com.eduerp.modules.chat.ChatConstants;
import com.eduerp.modules.chat.dto.ChatTurnPayload;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code GradeRules}. */
public final class ChatPromptRules {

    private ChatPromptRules() {
    }

    /**
     * Luật chống bịa đặt, copy nguyên văn spec mục 3.2. CỐ ĐỊNH trong code, không cấu hình được qua
     * property hay UI (spec mục 8): đây là thứ duy nhất ngăn chatbot hứa với phụ huynh một khóa học
     * hoặc một mức học phí không tồn tại, nên nó không được là một giá trị ai cũng sửa được lúc
     * runtime.
     */
    public static final String SYSTEM_PROMPT = """
            Bạn là trợ lý tư vấn của một trung tâm dạy thêm tại Việt Nam. CHỈ trả lời dựa trên danh \
            sách khóa học dưới đây - không bịa thêm khóa học, lịch học hay học phí nào không có trong \
            danh sách. Nếu không có khóa nào phù hợp với câu hỏi, hãy nói rõ là chưa tìm thấy và đề \
            nghị học viên liên hệ trung tâm. Luôn trả lời bằng tiếng Việt.""";

    static final String NO_MATCH_CONTEXT =
            "Không tìm thấy khóa học nào đang mở khớp với câu hỏi này.";

    static final String COURSE_LIST_HEADER = "Danh sách khóa học đang mở có liên quan:";

    private static final String MISSING_DESCRIPTION = "chưa có mô tả";
    private static final String MISSING_SESSION_COUNT = "chưa cấu hình tổng số buổi";
    private static final String MISSING_FEE = "chưa công bố học phí";

    /** Toàn bộ nội dung system message gửi cho provider: luật TRƯỚC, dữ liệu SAU. Thứ tự đó có chủ
     * đích - luật đặt sau dữ liệu sẽ bị một danh sách dài đẩy ra xa chỗ model "để ý" nhất. */
    public static String buildSystemText(List<CoursesManagement.OpenCourseSummary> courses) {
        return SYSTEM_PROMPT + "\n\n" + buildGroundingContext(courses);
    }

    /**
     * Review Focus #2: KHÔNG BAO GIỜ trả về chuỗi rỗng. Truy xuất rỗng là một câu khẳng định rõ ràng
     * ("không tìm thấy khóa nào"), vì một system message chỉ có luật mà không có dữ liệu nào là đúng
     * điều kiện khiến model tự lấp chỗ trống bằng khóa học nó tưởng tượng ra.
     */
    public static String buildGroundingContext(List<CoursesManagement.OpenCourseSummary> courses) {
        if (courses.isEmpty()) {
            return NO_MATCH_CONTEXT;
        }
        return COURSE_LIST_HEADER + "\n" + courses.stream()
                .map(ChatPromptRules::describe)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Review Focus #7: cắt bớt lịch sử xuống {@code MAX_HISTORY_TURNS} lượt GẦN NHẤT rồi nối câu hỏi
     * mới vào cuối. Cắt chứ không từ chối: lịch sử lớn dần là hành vi BÌNH THƯỜNG của một phiên chat
     * dài, không phải một client xấu tính, nên trả 400 ở lượt thứ 21 là làm hỏng tính năng.
     */
    public static List<AiChatTurn> toProviderTurns(List<ChatTurnPayload> history, String newMessage) {
        var recent = history.size() <= ChatConstants.Limits.MAX_HISTORY_TURNS
                ? history
                : history.subList(history.size() - ChatConstants.Limits.MAX_HISTORY_TURNS, history.size());
        var turns = new ArrayList<AiChatTurn>(recent.size() + 1);
        recent.forEach(turn -> turns.add(new AiChatTurn(toProviderRole(turn.role()), turn.content())));
        turns.add(new AiChatTurn(AiChatTurn.Role.USER, newMessage.strip()));
        return List.copyOf(turns);
    }

    /**
     * Review Focus #6: trần độ dài được quyết định ở backend. Đo sau {@code strip()} vì khoảng trắng
     * hai đầu là thứ người dán văn bản luôn kéo theo và không tốn token đáng kể.
     */
    public static boolean isMessageAcceptable(String message) {
        var trimmed = message.strip();
        return !trimmed.isEmpty() && trimmed.length() <= ChatConstants.Limits.MAX_MESSAGE_LENGTH;
    }

    private static AiChatTurn.Role toProviderRole(ChatConstants.TurnRole role) {
        return role == ChatConstants.TurnRole.ASSISTANT ? AiChatTurn.Role.ASSISTANT : AiChatTurn.Role.USER;
    }

    private static String describe(CoursesManagement.OpenCourseSummary course) {
        return "- " + course.name() + " (mã " + course.code() + "): " + describeDescription(course)
                + "; " + describeSessionCount(course) + "; " + describeFee(course);
    }

    private static String describeDescription(CoursesManagement.OpenCourseSummary course) {
        return course.description() == null || course.description().isBlank()
                ? MISSING_DESCRIPTION : course.description().strip();
    }

    private static String describeSessionCount(CoursesManagement.OpenCourseSummary course) {
        return course.standardSessionCount() == null
                ? MISSING_SESSION_COUNT : "tổng " + course.standardSessionCount() + " buổi";
    }

    private static String describeFee(CoursesManagement.OpenCourseSummary course) {
        return course.tuitionFee() == null
                ? MISSING_FEE : "học phí " + course.tuitionFee().toPlainString() + " VND";
    }
}
