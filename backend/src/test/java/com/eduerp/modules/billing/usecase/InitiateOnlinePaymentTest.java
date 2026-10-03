package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class InitiateOnlinePaymentTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
    private final PaymentGatewayClient client = mock(PaymentGatewayClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final InitiateOnlinePayment useCase =
            new InitiateOnlinePayment(invoices, payments, resolver, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), actorAccountId);
    }

    private void stubGateway() {
        when(resolver.resolve(BillingConstants.PaymentMethod.VNPAY)).thenReturn(client);
        when(client.createPaymentUrl(any(PaymentRequest.class))).thenAnswer(invocation -> {
            PaymentRequest request = invocation.getArgument(0);
            return new PaymentUrlResult("https://sandbox.vnpayment.vn/pay?ref=" + request.orderId(),
                    request.orderId());
        });
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, BillingConstants.PaymentMethod.VNPAY, actorAccountId,
                actorBranchId)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void rejectsAFullyPaidInvoice() {
        var invoice = invoiceOf("6000000");
        invoice.applyPayment(new BigDecimal("6000000"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY,
                actorAccountId, actorBranchId)).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectsACancelledInvoice() {
        var invoice = invoiceOf("6000000");
        invoice.cancel();
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY,
                actorAccountId, actorBranchId)).isInstanceOf(InvoiceNotPayableException.class);
    }

    /** Số tiền gửi sang cổng là phần CÒN LẠI, không phải tổng hoá đơn - nếu không, học viên đã đóng
     * một phần sẽ bị thu lại toàn bộ. */
    @Test
    void chargesOnlyTheRemainingAmountAndStoresAPendingPayment() {
        var invoice = invoiceOf("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        var response = useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId,
                actorBranchId);

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("4000000"));
        assertThat(saved.getValue().getMethod()).isEqualTo(BillingConstants.PaymentMethod.VNPAY);
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
        assertThat(saved.getValue().getPaidAt()).isNull();
        assertThat(response.payUrl()).contains(saved.getValue().getGatewayTransactionId());
    }

    /** orderId phải bắt đầu bằng invoiceId + số đợt (BillingRules.gatewayOrderId) - callback tra
     * ngược về Payment bằng đúng chuỗi này. */
    @Test
    void buildsAGatewayOrderIdFromTheInvoiceAndInstallment() {
        var invoice = invoiceOf("6000000");
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId, actorBranchId);

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getGatewayTransactionId())
                .startsWith(invoice.getId() + "-" + invoice.getInstallmentNumber() + "-");
    }

    /** Chưa có tiền thật nào vào thì chưa có gì để audit - event chỉ phát khi callback xác nhận
     * (spec mục 5 bước 7). */
    @Test
    void publishesNoEventBecauseNoMoneyHasArrivedYet() {
        var invoice = invoiceOf("6000000");
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId, actorBranchId);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void sendsTheInstallmentNumberInTheOrderInfoShownOnTheGateway() {
        var invoice = invoiceOf("6000000");
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId, actorBranchId);

        var sent = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(client).createPaymentUrl(sent.capture());
        assertThat(sent.getValue().orderInfo())
                .isEqualTo(BillingConstants.OrderInfo.PREFIX + invoice.getInstallmentNumber());
        assertThat(sent.getValue().returnUrl()).isNull();
        assertThat(sent.getValue().ipnUrl()).isNull();
    }
}
