package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MarkPayslipPaidRequest(@NotNull LocalDate paidOn, @NotNull PayrollConstants.PaymentMethod method,
        @Size(max = PayrollConstants.Limits.MAX_PAYMENT_NOTE_LENGTH) String note) {
}
