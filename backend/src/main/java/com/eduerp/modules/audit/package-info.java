/**
 * Module Audit — ghi lại các hành động đã xảy ra trong hệ thống. KHÔNG bị gọi trực tiếp: các module
 * khác publish sự kiện nghiệp vụ ({@code IdentityEvents}, {@code AccessEvents}, ...) qua
 * {@code ApplicationEventPublisher}, audit tự lắng nghe bằng {@code @ApplicationModuleListener} và
 * ghi log SAU KHI transaction phát sự kiện đã commit (rule #17) — identity/access không hề biết
 * audit tồn tại, chỉ audit biết chúng.
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.audit.AuditManagement} (facade đọc,
 *       dashboard dùng để lấy hoạt động gần đây).</li>
 *   <li>{@code dto} — hợp đồng đọc, có {@code @NamedInterface}.</li>
 *   <li>{@code internal.listener} — nơi duy nhất ghi {@code AuditLog}, kích hoạt bởi sự kiện chứ
 *       không phải HTTP hay AOP.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package com.eduerp.modules.audit;
