package com.eduerp.integrations.notification;

import com.eduerp.integrations.notification.internal.SseEmitterRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Facade của module notification — type DUY NHẤT mà module khác được phép gọi (rule #1).
 */
@Service
public class NotificationManagement {

    private final SseEmitterRegistry registry;

    NotificationManagement(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    public SseEmitter subscribe(UUID accountId) {
        return registry.register(accountId);
    }

    public void push(UUID accountId, String type, Object payload) {
        registry.send(accountId, type, payload);
    }
}
