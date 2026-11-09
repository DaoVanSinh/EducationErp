/**
 * Module Payroll - sở hữu kỳ lương và phiếu lương (PayrollRun/Payslip). Tính lương từ điều khoản trên hồ sơ
 * giáo viên và giờ dạy từ điểm danh.
 *
 * <p>Phụ thuộc một chiều {@code access} (AccessConstants cho @PreAuthorize), {@code identity} (họ tên, chi nhánh chủ quản),
 * {@code organization} (tên chi nhánh),
 * {@code teachers} (điều khoản lương) và {@code attendance} (giờ dạy). {@code audit} chỉ lắng nghe
 * {@code PayrollEvents}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payroll")
package com.eduerp.modules.payroll;
