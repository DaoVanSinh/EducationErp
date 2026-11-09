package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một kỳ lương. {@code periodStart}/{@code periodEnd} nói rõ khoảng ngày được tính; {@code rejectionReason} có khi
 * kỳ vừa bị trả về nháp; {@code totalEmployerInsurance} là phần BHXH trung tâm đóng thêm ngoài lương.
 */
public record PayrollRunResponse(UUID id, int year, int month, LocalDate periodStart, LocalDate periodEnd,
        PayrollConstants.PayrollRunStatus status, String rejectionReason, int payslipCount, BigDecimal totalGrossPay,
        BigDecimal totalNetPay, BigDecimal totalEmployerInsurance) {
}
