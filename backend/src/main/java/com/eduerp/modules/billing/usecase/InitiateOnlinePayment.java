package com.eduerp.modules.billing.usecase;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayException;
import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.PaymentGatewayUnavailableException;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo link/QR thanh toán cho phần CÒN LẠI của hoá đơn và ghi một {@code Payment} ở trạng thái
 * {@code PENDING}. KHÔNG publish event ở đây: tiền chưa thật, chỉ callback/IPN mới là nguồn sự thật
 * (spec mục 5 bước 7).
 */
@Service
public class InitiateOnlinePayment {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentGatewayClientResolver gateways;
    private final ApplicationEventPublisher events;

    InitiateOnlinePayment(InvoiceRepository invoices, PaymentRepository payments,
            PaymentGatewayClientResolver gateways, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.gateways = gateways;
        this.events = events;
    }

    @Transactional
    public InitiateOnlinePaymentResponse execute(UUID invoiceId, BillingConstants.PaymentMethod gateway,
            UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!BillingRules.isPayable(invoice.getStatus())) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }

        var client = gateways.resolve(gateway);
        var remaining = BillingRules.remaining(invoice.getAmount(), invoice.getAmountPaid());
        var orderId = BillingRules.gatewayOrderId(invoiceId, invoice.getInstallmentNumber(),
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
