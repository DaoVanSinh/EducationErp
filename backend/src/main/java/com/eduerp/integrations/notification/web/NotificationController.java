package com.eduerp.integrations.notification.web;

import com.eduerp.integrations.notification.NotificationManagement;
import com.eduerp.shared.AccountPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Không @PreAuthorize: ai đăng nhập được thì được nghe đúng kênh của chính mình (subscribe tự khoá theo accountId). */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationManagement notifications;

    NotificationController(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal AccountPrincipal principal) {
        return notifications.subscribe(principal.accountId());
    }
}
