package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.AccessEvents;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Một chỗ duy nhất cho "quyền của các tài khoản này vừa đổi": xoá cache SAU commit (rule #6 - rollback
 * thì không xoá cho một thay đổi chưa từng xảy ra) và phát {@link AccessEvents.PermissionsChanged} để
 * kênh SSE báo trình duyệt của họ tải lại phiên.
 *
 * <p>Đăng ký việc xoá cache TRƯỚC khi publish sự kiện: listener SSE cũng chạy sau commit, và
 * synchronization được gọi theo thứ tự đăng ký - trình duyệt nhận tín hiệu thì cache đã sạch.
 */
@Component
public class PermissionChangeNotifier {

    private final PermissionCacheService cache;
    private final ApplicationEventPublisher events;

    PermissionChangeNotifier(PermissionCacheService cache, ApplicationEventPublisher events) {
        this.cache = cache;
        this.events = events;
    }

    public void permissionsChanged(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return;
        }
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(accountIds));
        evictAfterCommit(distinct);
        events.publishEvent(new AccessEvents.PermissionsChanged(distinct));
    }

    /** Chỉ xoá cache, không báo ai - dùng khi gán vai trò cho tài khoản vừa tạo (chưa có phiên nào). */
    public void evictAfterCommit(Collection<UUID> accountIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accountIds.forEach(cache::evict);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accountIds.forEach(cache::evict);
            }
        });
    }
}
