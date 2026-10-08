package com.eduerp.modules.billing.dto;

import java.util.List;

/** Mirror {@code InvoiceDetailResponse(invoice, payments)}: tổng thể + các danh sách con. */
public record ComboDetailResponse(ComboResponse combo, List<ComboEnrollmentResponse> enrollments,
        List<InvoiceResponse> invoices) {
}
