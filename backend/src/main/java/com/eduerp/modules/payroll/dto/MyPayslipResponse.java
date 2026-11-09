package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.teachers.TeachersConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một phiếu lương trong tab "Lương" của trang Hồ sơ của tôi, chỉ từ kỳ đã duyệt. Có đủ cách tính để giáo viên tự
 * đối chiếu; không có phần BHXH trung tâm đóng vì đó là chi phí của trung tâm. {@code paidOn} null là chờ thanh toán.
 */
public record MyPayslipResponse(UUID id, int year, int month, LocalDate periodStart, LocalDate periodEnd,
        TeachersConstants.PayType payType, BigDecimal payRate, int sessionCount, BigDecimal hoursWorked, int paidDays,
        int probationDays, BigDecimal grossPay, BigDecimal allowancesTotal, BigDecimal adjustment,
        BigDecimal socialInsuranceEmployee, BigDecimal incomeTaxWithheld, BigDecimal netPay, String note, LocalDate paidOn,
        PayrollConstants.PaymentMethod paymentMethod) {
}
