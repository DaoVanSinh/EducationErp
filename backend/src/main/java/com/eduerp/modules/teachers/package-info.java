/**
 * Module Teachers — sở hữu hồ sơ nghiệp vụ của giáo viên (môn dạy, liên hệ, điều khoản lương), tách khỏi
 * Account chung của identity. Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho
 * {@code @PreAuthorize}), {@code identity} (tạo tài khoản, hiển thị tên/email) và {@code courses} (lọc theo
 * chi nhánh đang dạy). {@code landing} đọc danh sách giáo viên, {@code payroll} đọc điều khoản lương qua
 * {@link com.eduerp.modules.teachers.TeachersManagement}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Teachers")
package com.eduerp.modules.teachers;
