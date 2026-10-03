package com.eduerp.integrations.notification.internal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory hợp lý vì stack là 1 deployable duy nhất (mục 3 spec tổng) - không multi-instance, không
 * cần Redis pub/sub để đồng bộ giữa các instance.
 */
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID accountId) {
        var emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(accountId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(accountId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        return emitter;
    }

    public void send(UUID accountId, String type, Object payload) {
        var list = emitters.get(accountId);
        if (list == null) {
            return;
        }
        for (var emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(type).data(payload == null ? "" : payload));
            } catch (IOException e) {
                remove(accountId, emitter);
            }
        }
    }

    public int emitterCountFor(UUID accountId) {
        var list = emitters.get(accountId);
        return list == null ? 0 : list.size();
    }

    private void remove(UUID accountId, SseEmitter emitter) {
        emitters.computeIfPresent(accountId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
