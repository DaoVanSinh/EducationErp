package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateContractRequest(BigDecimal baseSalary, BigDecimal hourlyRate, LocalDate probationEndDate,
        @NotNull List<AllowanceRequest> allowances) {
}
