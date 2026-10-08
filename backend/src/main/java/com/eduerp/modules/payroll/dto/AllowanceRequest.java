package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AllowanceRequest(@NotBlank @Size(max = 100) String name, @NotNull BigDecimal amount) {
}
