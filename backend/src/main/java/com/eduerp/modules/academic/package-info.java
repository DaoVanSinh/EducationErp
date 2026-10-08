/**
 * Module Academic — sở hữu ĐIỂM thật ({@code GradeEntry}) và việc TÍNH tiến trình học tập. Đây là
 * nơi duy nhất biết "một học viên trong một lớp đang ở đâu" về mặt học thuật, tách khỏi
 * {@code courses} (danh mục), {@code enrollment} (ai học lớp nào) và {@code billing} (ai nợ bao
 * nhiêu) — tránh God Module (spec mục 3).
 *
 * <p>Phụ thuộc một chiều: {@code access} ({@code AccessConstants} cho {@code @PreAuthorize} và
 * {@code AccessManagement.effectivePermissions} để biết scope THẬT của caller), {@code courses}
 * ({@code CoursesManagement.getClassInfo} lấy {@code teacherId}/lịch học/số buổi chuẩn,
 * {@code .getGradeColumns} lấy cấu trúc điểm), {@code enrollment}
 * ({@code EnrollmentManagement.listActiveEnrollmentsByClass}), {@code students} +
 * {@code identity} (tên học viên in trên bảng điểm). KHÔNG module nào đọc ngược từ
 * {@code academic} ở phase này; {@code audit} chỉ lắng nghe {@code AcademicEvents} bằng
 * {@code @ApplicationModuleListener}, không có cạnh phụ thuộc ngược.
 *
 * <p>KHÔNG LƯU tiến trình học tập: không có cột DB nào giữ %, con số được tính lúc đọc từ
 * {@code Enrollment.enrolledAt}/{@code withdrawnAt} đối chiếu lịch tuần của lớp (spec mục 5).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: constants, exception, events. Không có facade
 *       {@code AcademicManagement} vì chưa module nào cần đọc điểm (rule #1 — chỉ tạo facade khi
 *       có nơi gọi thật).</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Academic (Grades & Progress)")
package com.eduerp.modules.academic;
