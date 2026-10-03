package com.eduerp.modules.payroll.internal.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Kết quả thô của phép đếm/cộng dồn payslip theo kỳ lương; usecase là nơi đổi nó thành DTO. */
public interface PayrollRunAggregateRow {

    UUID getPayrollRunId();

    long getPayslipCount();

    BigDecimal getTotalGrossPay();
}
