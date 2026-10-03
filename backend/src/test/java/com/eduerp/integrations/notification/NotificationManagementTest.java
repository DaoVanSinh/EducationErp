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

    /**
     * SseEmitter.send() trên một emitter đã complete() ném IllegalStateException, không phải
     * IOException - một tab đã đóng không được phép chặn các tab khác của cùng account nhận thông báo.
     */
    @Test
    void aCompletedEmitterDoesNotStopOtherEmittersOfTheSameAccountFromReceiving() {
        var registry = new SseEmitterRegistry();
        var notifications = new NotificationManagement(registry);
        var accountId = UUID.randomUUID();

        var staleEmitter = notifications.subscribe(accountId);
        notifications.subscribe(accountId);
        staleEmitter.complete();

        notifications.push(accountId, "PING", "hello");
    }
}
