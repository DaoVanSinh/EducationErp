package com.eduerp.modules.billing.internal;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayException;
import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.PaymentGatewayUnavailableException;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Phần chung của "tạo link thanh toán online" cho MỘT hoá đơn đã qua kiểm quyền: kế toán thu hộ
 * ({@code InitiateOnlinePayment}, kiểm phạm vi chi nhánh) và học viên tự trả
 * ({@code InitiateMyOnlinePayment}, kiểm hoá đơn là của chính mình) khác nhau ở bước kiểm quyền,
 * giống nhau từ đây trở đi. Không tự mở transaction - chạy trong transaction của use case gọi nó.
 *
 * <p>Link luôn tính cho phần CÒN LẠI của hoá đơn và ghi một {@code Payment} PENDING. KHÔNG publish
 * event: tiền chưa thật, chỉ callback/IPN đã xác thực chữ ký mới là nguồn sự thật.
 */
@Component
public class OnlinePaymentStarter {

    private final PaymentRepository payments;
    private final PaymentGatewayClientResolver gateways;

    public OnlinePaymentStarter(PaymentRepository payments, PaymentGatewayClientResolver gateways) {
        this.payments = payments;
        this.gateways = gateways;
    }

    public InitiateOnlinePaymentResponse start(Invoice invoice, BillingConstants.PaymentMethod gateway) {
        if (!BillingRules.isPayable(invoice.getStatus())) {
            throw new InvoiceNotPayableException(invoice.getId(), invoice.getStatus());
        }

        var client = gateways.resolve(gateway);
        var remaining = BillingRules.remaining(invoice.getAmount(), invoice.getAmountPaid());
        var orderId = BillingRules.gatewayOrderId(invoice.getId(), invoice.getInstallmentNumber(),
                System.currentTimeMillis());
        var orderInfo = BillingConstants.OrderInfo.PREFIX + invoice.getInstallmentNumber();

        var urlResult = createPaymentUrl(client, orderId, remaining, orderInfo);
        payments.save(new Payment(invoice, remaining, gateway, urlResult.gatewayOrderId(),
                BillingConstants.PaymentStatus.PENDING));
        return new InitiateOnlinePaymentResponse(urlResult.payUrl());
    }

    /**
     * Final review Critical #1: cổng có thể từ chối tạo link (chữ ký cấu hình sai, amount không hợp
     * lệ, đối tác bị khoá...). Dịch sang lỗi nghiệp vụ TRƯỚC khi lưu {@code Payment} - nếu không, một
     * lần cổng từ chối vẫn để lại một bản ghi PENDING mồ côi mà không ai biết để đối soát.
     */
    private static PaymentUrlResult createPaymentUrl(PaymentGatewayClient client, String orderId,
            BigDecimal remaining, String orderInfo) {
        try {
            return client.createPaymentUrl(PaymentRequest.withGatewayDefaults(orderId, remaining, orderInfo));
        } catch (PaymentGatewayException gatewayFailure) {
            throw new PaymentGatewayUnavailableException(gatewayFailure.getMessage());
        }
    }
}
