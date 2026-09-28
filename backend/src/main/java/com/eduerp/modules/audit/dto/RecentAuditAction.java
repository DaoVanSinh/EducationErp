package com.eduerp.modules.audit.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng audit thô. {@code entityId} là chuỗi vì có thể mang UUID của bất kỳ loại tài nguyên nào
 * (account, role, permission group...) tuỳ vào {@code entityType} đã lọc. Ghép tên/email của actor
 * là việc của module gọi (rule #3).
 */
public record RecentAuditAction(UUID actorAccountId, String entityId, Instant occurredAt) {
}
