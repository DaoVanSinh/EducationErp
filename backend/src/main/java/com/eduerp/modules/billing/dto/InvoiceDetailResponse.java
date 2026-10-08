package com.eduerp.modules.billing.dto;

import java.util.List;

/** Mirror {@code PayrollRunDetailResponse(run, payslips)}: tổng thể + danh sách con. */
public record InvoiceDetailResponse(InvoiceResponse invoice, List<PaymentResponse> payments) {
}
