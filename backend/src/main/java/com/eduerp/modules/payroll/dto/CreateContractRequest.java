package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateContractRequest(@NotNull UUID accountId, @NotNull PayrollConstants.ContractType contractType,
        BigDecimal baseSalary, BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate,
        @NotNull LocalDate startDate, @NotNull List<AllowanceRequest> allowances) {
}
