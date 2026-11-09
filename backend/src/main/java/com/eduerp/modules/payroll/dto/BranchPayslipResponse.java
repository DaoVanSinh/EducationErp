package com.eduerp.modules.payroll.dto;

/** Một phiếu đã duyệt để chi trả, kèm tháng vì danh sách này đi xuyên nhiều kỳ lương. */
public record BranchPayslipResponse(int year, int month, PayslipResponse payslip) {
}
