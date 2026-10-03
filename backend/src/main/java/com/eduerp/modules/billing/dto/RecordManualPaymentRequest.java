package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RecordManualPaymentRequest(@NotNull @Positive BigDecimal amount) {
}
