package com.eduerp.modules.payroll.internal.rules;

import com.eduerp.modules.teachers.TeachersConstants;
import java.math.BigDecimal;

/** Kết quả công thức lương - mọi con số ghi xuống phiếu. {@code payRate}: lương tháng hoặc đơn giá giờ. */
public record PayslipFigures(TeachersConstants.PayType payType, BigDecimal payRate, int sessionCount,
        BigDecimal hoursWorked, int paidDays, int probationDays, int dependentCount, BigDecimal grossPay,
        BigDecimal allowancesTotal, BigDecimal socialInsuranceEmployee, BigDecimal socialInsuranceEmployer,
        BigDecimal incomeTaxWithheld) {
}
