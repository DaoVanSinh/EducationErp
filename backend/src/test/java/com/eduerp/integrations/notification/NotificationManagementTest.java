package com.eduerp.integrations.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.notification.internal.SseEmitterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationManagementTest {

    @Test
    void subscribeRegistersExactlyOneEmitterPerAccount() {
        var registry = new SseEmitterRegistry();
        var notifications = new NotificationManagement(registry);
        var accountId = UUID.randomUUID();

        var emitter = notifications.subscribe(accountId);

        assertThat(emitter).isNotNull();
        assertThat(registry.emitterCountFor(accountId)).isEqualTo(1);
    }

    @Test
    void pushToAnUnsubscribedAccountDoesNothingAndDoesNotThrow() {
        var registry = new SseEmitterRegistry();
        var notifications = new NotificationManagement(registry);

        notifications.push(UUID.randomUUID(), "PING", "hello");
    }
}
