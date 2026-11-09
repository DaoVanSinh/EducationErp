package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.teachers.TeachersConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phiếu lương trong kỳ, đủ số liệu để tự kiểm tra công thức. {@code payRate} là lương tháng hoặc đơn giá giờ
 * tuỳ {@code payType}; {@code hoursWorked} luôn lấy từ điểm danh; {@code paidDays}/{@code probationDays} chỉ có ý
 * nghĩa với trả theo tháng. {@code branchId} là chi nhánh chịu trách nhiệm chi trả; {@code paidOn} null là chưa trả;
 * {@code paymentNote} là ghi chú lúc trả, hoặc lý do nếu admin gốc đã huỷ đánh dấu nhầm.
 */
public record PayslipResponse(UUID id, UUID accountId, String accountFullName, TeachersConstants.PayType payType,
        BigDecimal payRate, int sessionCount, BigDecimal hoursWorked, int paidDays, int probationDays,
        int dependentCount, BigDecimal grossPay, BigDecimal allowancesTotal, BigDecimal adjustment,
        BigDecimal socialInsuranceEmployee, BigDecimal socialInsuranceEmployer, BigDecimal incomeTaxWithheld,
        BigDecimal netPay, String note, UUID branchId, String branchName, LocalDate paidOn,
        PayrollConstants.PaymentMethod paymentMethod, String paymentNote) {
}
