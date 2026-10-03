package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectPayrollRunRequest(@NotBlank String reason) {
}
