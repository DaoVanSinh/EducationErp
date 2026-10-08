/**
 * Nơi DUY NHẤT ghi {@code AuditLog}. Mỗi method lắng nghe một sự kiện nghiệp vụ do module khác
 * publish, chạy sau khi transaction phát sự kiện đã commit (rule #17) — thay hẳn cơ chế AOP
 * {@code @Audited} trước đây, vốn chỉ đúng khi mọi use case audited nằm chung một module.
 */
package com.eduerp.modules.audit.internal.listener;
