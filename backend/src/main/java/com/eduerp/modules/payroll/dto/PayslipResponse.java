package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.util.UUID;

public record PayslipResponse(UUID id, UUID accountId, String accountFullName,
        PayrollConstants.ContractType contractType, BigDecimal grossPay, BigDecimal socialInsuranceEmployee,
        BigDecimal socialInsuranceEmployer, BigDecimal incomeTaxWithheld, BigDecimal netPay, BigDecimal hoursWorked,
        boolean inProbation) {
}
