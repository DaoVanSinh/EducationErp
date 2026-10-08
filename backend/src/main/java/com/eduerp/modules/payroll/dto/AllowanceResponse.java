package com.eduerp.modules.payroll.dto;

import java.math.BigDecimal;

public record AllowanceResponse(String name, BigDecimal amount) {
}
