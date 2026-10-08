package com.eduerp.modules.billing.web;

import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.usecase.GetPaymentStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trạng thái thật của một giao dịch, cho trang {@code /payment/return/:gateway} ở frontend poll lại
 * sau khi phụ huynh quay về từ cổng (IPN là nguồn sự thật và xử lý bất đồng bộ - spec mục 10).
 *
 * <p>PUBLIC, không {@code @PreAuthorize}: phụ huynh có thể chưa đăng nhập khi được redirect về. Đây
 * là điểm plan cố ý khác bảng quyền ở spec mục 5 (ở đó ghi {@code READ_INVOICE}) - với
 * {@code READ_INVOICE} thì trang Return URL không thể hoạt động đúng như spec mục 10 mô tả.
 * {@code gatewayTransactionId} ({@code invoiceId}-{@code installment}-{@code millis}) không đoán
 * được nên đóng vai capability token, và {@link PaymentResponse} không chứa dữ liệu cá nhân nào -
 * {@code BillingReadUseCasesTest} chốt lại hình dạng đó bằng reflection.
 */
@RestController
@RequestMapping("/api/billing/payments")
class PaymentStatusController {

    private final GetPaymentStatus getPaymentStatus;

    PaymentStatusController(GetPaymentStatus getPaymentStatus) {
        this.getPaymentStatus = getPaymentStatus;
    }

    @GetMapping("/{gatewayTransactionId}/status")
    PaymentResponse get(@PathVariable String gatewayTransactionId) {
        return getPaymentStatus.execute(gatewayTransactionId);
    }
}
