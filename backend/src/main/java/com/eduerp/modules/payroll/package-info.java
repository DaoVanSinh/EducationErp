/**
 * Module Payroll & Nhân sự - sở hữu hợp đồng lao động (EmploymentContract) và chu kỳ lương
 * (PayrollRun/Payslip). Gộp cả hai vì luôn dùng cùng nhau trong một vòng nghiệp vụ (hợp đồng là
 * input để tính lương) - tách modules.hr riêng là over-engineering khi chưa có use case nào cần
 * EmploymentContract độc lập mà không liên quan lương (spec mục 3.1).
 *
 * <p>Phụ thuộc một chiều {@code access} (AccessConstants cho @PreAuthorize), {@code identity}
 * (IdentityManagement.summariesOf để hiển thị tên/email) và {@code integrations.storage}
 * (StorageClient để lưu file hợp đồng PDF). Không module nào đọc ngược từ {@code payroll}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payroll & Nhân sự")
package com.eduerp.modules.payroll;
