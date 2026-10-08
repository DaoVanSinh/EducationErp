package com.eduerp.modules.chat.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.chat.dto.AskChatbotRequest;
import com.eduerp.modules.chat.dto.AskChatbotResponse;
import com.eduerp.modules.chat.usecase.AskChatbot;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint DUY NHẤT của Phân hệ 5.
 *
 * <p>Tên class giữ hậu tố {@code AdminController} theo đúng tên spec mục 3.2 đặt, để truy vết
 * plan↔spec không đứt - nhưng người dùng chính ở đây là HỌC VIÊN, không phải admin. Khác mọi
 * controller khác trong dự án, {@code @PreAuthorize} ở đây KHÔNG đòi scope {@code ORGANIZATION}:
 * học viên chỉ được cấp {@code CHAT:CREATE:PERSONAL} (V27), và ở đây không có khái niệm sở hữu tài
 * nguyên nào cần kiểm thêm, nên cũng không có guard nào trong usecase như
 * {@code ClassGradeAccessGuard} của Phân hệ 4 - xem javadoc
 * {@code AccessConstants.AccessRules.CREATE_CHAT}.
 *
 * <p>POST (không GET) dù đây là một thao tác đọc: câu hỏi và cả lịch sử hội thoại nằm trong body
 * (quá dài cho query string), và một câu hỏi của học viên không được phép lọt vào access log của
 * mọi proxy trên đường đi.
 */
@RestController
@RequestMapping("/api/chat")
class ChatAdminController {

    private final AskChatbot askChatbot;

    ChatAdminController(AskChatbot askChatbot) {
        this.askChatbot = askChatbot;
    }

    @PostMapping("/messages")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_CHAT)
    AskChatbotResponse ask(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AskChatbotRequest request) {
        return askChatbot.execute(principal.accountId(), request);
    }
}
