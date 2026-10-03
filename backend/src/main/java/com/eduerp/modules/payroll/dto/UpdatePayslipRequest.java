package com.eduerp.modules.payroll.dto;

import java.math.BigDecimal;

/** hoursWorked chỉ có ý nghĩa với COLLABORATOR; incomeTaxWithheld là ô TNCN nhập tay (spec mục 2). */
public record UpdatePayslipRequest(BigDecimal hoursWorked, BigDecimal incomeTaxWithheld, String note) {
}
