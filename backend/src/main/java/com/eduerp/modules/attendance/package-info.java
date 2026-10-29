/**
 * Module Attendance — điểm danh theo buổi của một lớp (đề cương ĐATN, Phân hệ Quản lý Học tập:
 * "Giảng viên xem danh sách lớp, quản lý điểm danh"). Sở hữu {@code attendance_sessions} và
 * {@code attendance_records}.
 *
 * <p>Tách khỏi {@code academic} (điểm + tiến trình) để tránh God Module: điểm danh có vòng đời và
 * vốn từ riêng (buổi, có mặt/muộn/vắng/có phép), không chia sẻ dữ liệu nào với điểm số.
 *
 * <p>Phụ thuộc một chiều: {@code access} ({@code AccessConstants} cho {@code @PreAuthorize},
 * {@code AccessManagement.effectivePermissions} để biết scope thật), {@code courses}
 * ({@code getClassInfo} lấy {@code teacherId}, {@code classSummariesOf} lấy mã lớp/tên khóa),
 * {@code enrollment} (danh sách học viên của lớp, ghi danh của một học viên), {@code students}
 * (hồ sơ theo tài khoản, họ tên). {@code audit} chỉ lắng nghe {@code AttendanceEvents}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Attendance")
package com.eduerp.modules.attendance;
