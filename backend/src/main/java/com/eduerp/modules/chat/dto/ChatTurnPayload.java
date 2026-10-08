package com.eduerp.modules.chat.dto;

import com.eduerp.modules.chat.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một lượt hội thoại do CLIENT gửi lên (spec mục 3.2). Backend không lưu gì nên đây là nguồn duy
 * nhất của lịch sử - và vì nó đến từ client, nó là dữ liệu không tin được: {@code @Size} chặn một
 * lượt lịch sử dài hơn mức một tin nhắn từng được phép, còn số LƯỢNG lượt do
 * {@code ChatPromptRules.toProviderTurns} cắt bớt (Review Focus #7).
 */
public record ChatTurnPayload(@NotNull ChatConstants.TurnRole role,
        @NotBlank @Size(max = ChatConstants.Limits.MAX_MESSAGE_LENGTH) String content) {
}
