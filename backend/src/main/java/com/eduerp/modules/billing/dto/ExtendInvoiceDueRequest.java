package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ExtendInvoiceDueRequest(@NotNull LocalDate dueDate,
        @NotBlank @Size(max = BillingConstants.Limits.MAX_EXTENSION_REASON_LENGTH) String reason) {
}
