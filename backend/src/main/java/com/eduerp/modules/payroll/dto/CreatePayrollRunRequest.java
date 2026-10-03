package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreatePayrollRunRequest(@Min(2000) int year, @Min(1) @Max(12) int month) {
}
