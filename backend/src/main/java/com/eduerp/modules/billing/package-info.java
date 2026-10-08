/**
 * Module Billing — sở hữu hoá đơn học phí (Invoice, tối đa 3 đợt mỗi ghi danh) và lịch sử thanh
 * toán (Payment). Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho
 * {@code @PreAuthorize}), {@code enrollment} ({@code EnrollmentManagement.getEnrollment} để chốt
 * snapshot và chặn phát hành cho ghi danh đã rút), {@code courses}
 * ({@code CoursesManagement.getCourseTuition} để chặn tổng các đợt vượt học phí) và
 * {@code integrations.payment} ({@code PaymentGatewayClient} - giống cách {@code modules.payroll}
 * gọi {@code integrations.storage}). Không module nào đọc ngược từ {@code billing} (spec mục 3).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — constants, exception, events (chưa có facade: chưa module nào cần đọc
 *       billing, không suy đoán method chưa ai gọi - mirror quyết định ở CoursesManagement).</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8), gồm cả 3 endpoint public cho cổng thanh toán.</li>
 *   <li>{@code internal} — entity/repository/rules + {@code OverdueInvoiceScheduler}.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Billing & Tuition")
package com.eduerp.modules.billing;
