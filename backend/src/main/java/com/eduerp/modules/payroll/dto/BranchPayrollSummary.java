package com.eduerp.modules.payroll.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Tiến độ chi trả của một chi nhánh trong kỳ. {@code branchId} null là giáo viên chưa thuộc chi nhánh nào. */
public record BranchPayrollSummary(UUID branchId, String branchName, int payslipCount, int paidCount,
        BigDecimal totalNetPay) {
}
