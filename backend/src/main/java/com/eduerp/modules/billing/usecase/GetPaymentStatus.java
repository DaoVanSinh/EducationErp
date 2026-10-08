package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.PaymentNotFoundException;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dùng cho trang Return URL ở frontend: IPN là nguồn sự thật và xử lý bất đồng bộ, nên lúc phụ huynh
 * được redirect về, trạng thái có thể còn {@code PENDING} - trang phải poll lại API thay vì tin query
 * param trên URL (spec mục 10).
 */
@Service
public class GetPaymentStatus {

    private final PaymentRepository payments;

    GetPaymentStatus(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PaymentResponse execute(String gatewayTransactionId) {
        return payments.findByGatewayTransactionId(gatewayTransactionId)
                .map(GetInvoiceDetail::toPaymentResponse)
                .orElseThrow(() -> new PaymentNotFoundException(gatewayTransactionId));
    }
}
