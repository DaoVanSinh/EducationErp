package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContractResponse(UUID id, UUID accountId, String accountFullName, String accountEmail,
        PayrollConstants.ContractType contractType, PayrollConstants.ContractStatus status, BigDecimal baseSalary,
        BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate startDate,
        LocalDate endDate, List<AllowanceResponse> allowances, String contractFileKey) {
}
