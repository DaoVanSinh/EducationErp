package com.eduerp.modules.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần đăng nhập gần đây, đã ghép sẵn tên người dùng để màn hình dashboard không phải gọi thêm
 * API thứ hai cho từng dòng.
 */
public record RecentLoginResponse(UUID accountId, String email, String fullName, Instant occurredAt) {
}
