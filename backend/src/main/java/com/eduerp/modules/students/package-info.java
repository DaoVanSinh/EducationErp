/**
 * Module Students — sở hữu hồ sơ nghiệp vụ của học viên, tách khỏi Account chung của identity. Phụ
 * thuộc một chiều {@code access} ({@code AccessConstants} cho {@code @PreAuthorize},
 * {@code AccessManagement.roleOf} để xác nhận accountId có role STUDENT) và {@code identity}
 * ({@code IdentityManagement.summariesOf} để hiển thị tên/email). Không module nào đọc ngược từ
 * {@code students} ở phase này.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Students")
package com.eduerp.modules.students;
