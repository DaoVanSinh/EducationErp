package com.eduerp.modules.billing.usecase;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentSignatureException;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidCallbackSignatureException;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nguồn sự thật của việc tiền đã vào. Ba quy tắc không được phá:
 * <ol>
 *   <li>Chữ ký sai → {@link InvalidCallbackSignatureException}, KHÔNG tra cứu gì thêm (không để lộ
 *       orderId nào tồn tại qua thời gian phản hồi hay side effect).</li>
 *   <li>{@code orderId} lạ → chỉ log cảnh báo rồi return, KHÔNG ném (Review Focus #5).</li>
 *   <li>{@code Payment.status != PENDING} → return ngay (Review Focus #4: callback gọi 2 lần không
 *       được cộng tiền 2 lần, và một callback thất bại đến muộn không được hạ bản ghi đã SUCCESS).</li>
 * </ol>
 * Số tiền ghi nhận lấy từ {@code Payment.amount} (hệ thống tự tính lúc tạo link), không lấy từ
 * {@code result.amount()} - không để một payload hợp lệ về chữ ký nhưng sai số tiền ghi sai công nợ.
 */
@Service
public class HandlePaymentCallback {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentCallback.class);

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentGatewayClientResolver gateways;
    private final ApplicationEventPublisher events;

    HandlePaymentCallback(InvoiceRepository invoices, PaymentRepository payments,
            PaymentGatewayClientResolver gateways, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.gateways = gateways;
        this.events = events;
    }

    @Transactional
    public void execute(PaymentGatewayType gatewayType, Map<String, String> rawParams) {
        var result = verify(gatewayType, rawParams);
        var payment = payments.findByGatewayTransactionId(result.orderId()).orElse(null);
        if (payment == null) {
            log.warn("Bỏ qua callback của cổng {}: không có giao dịch nào khớp mã nhận được", gatewayType);
            return;
        }
        if (payment.getStatus() != BillingConstants.PaymentStatus.PENDING) {
            log.info("Bỏ qua callback trùng của cổng {}: giao dịch đã ở trạng thái {}", gatewayType,
                    payment.getStatus());
            return;
        }
        if (result.success()) {
            applySuccess(payment);
            return;
        }
        payment.markFailed();
    }

    private PaymentCallbackResult verify(PaymentGatewayType gatewayType, Map<String, String> rawParams) {
        try {
            return gateways.resolve(gatewayType).verifyCallback(rawParams);
        } catch (PaymentSignatureException badSignature) {
            throw new InvalidCallbackSignatureException(gatewayType);
        }
    }

    private void applySuccess(Payment payment) {
        payment.markSucceeded();
        var invoice = payment.getInvoice();
        invoice.applyPayment(payment.getAmount());
        invoices.save(invoice);
        events.publishEvent(new BillingEvents.PaymentReceived(invoice.getId(), invoice.getCreatedByAccountId(),
                invoice.getBranchId()));
    }
}
