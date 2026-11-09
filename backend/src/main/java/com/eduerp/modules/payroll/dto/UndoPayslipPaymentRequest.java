package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UndoPayslipPaymentRequest(
        @NotBlank @Size(max = PayrollConstants.Limits.MAX_PAYMENT_NOTE_LENGTH) String reason) {
}
