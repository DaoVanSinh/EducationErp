package com.eduerp.modules.chat.dto;

import com.eduerp.modules.chat.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một lượt hội thoại do CLIENT gửi lên (spec mục 3.2). Backend không lưu gì nên đây là nguồn duy
 * nhất của lịch sử - và vì nó đến từ client, nó là dữ liệu không tin được: {@code @Size} chặn một
 * lượt lịch sử dài hơn một câu trả lời thật có thể có, còn số LƯỢNG lượt do
 * {@code ChatPromptRules.toProviderTurns} cắt bớt (Review Focus #7).
 *
 * <p>Final review Critical #2: trần dùng {@code MAX_HISTORY_TURN_LENGTH}, KHÔNG phải
 * {@code MAX_MESSAGE_LENGTH} - xem javadoc của nó. Một lượt ở đây có thể là câu trả lời CỦA CHÍNH
 * CHATBOT mà client gửi lại nguyên văn, bị giới hạn bởi {@code max-tokens}, không phải bởi trần của
 * một câu hỏi MỚI.
 */
public record ChatTurnPayload(@NotNull ChatConstants.TurnRole role,
        @NotBlank @Size(max = ChatConstants.Limits.MAX_HISTORY_TURN_LENGTH) String content) {
}
