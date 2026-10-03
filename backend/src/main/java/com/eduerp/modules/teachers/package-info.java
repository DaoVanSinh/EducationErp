/**
 * Module Teachers — sở hữu hồ sơ nghiệp vụ của giáo viên (môn dạy, liên hệ), tách khỏi Account chung
 * của identity. Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho {@code @PreAuthorize},
 * {@code AccessManagement.roleOf} để xác nhận accountId có role TEACHER) và {@code identity}
 * ({@code IdentityManagement.summariesOf} để hiển thị tên/email). Không module nào đọc ngược từ
 * {@code teachers} ở phase này.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Teachers")
package com.eduerp.modules.teachers;
