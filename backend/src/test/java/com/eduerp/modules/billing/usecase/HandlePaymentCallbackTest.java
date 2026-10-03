package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentSignatureException;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidCallbackSignatureException;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class HandlePaymentCallbackTest {

    private static final String ORDER_ID = "order-1";
    private static final Map<String, String> RAW_PARAMS = Map.of("vnp_TxnRef", ORDER_ID);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
    private final PaymentGatewayClient client = mock(PaymentGatewayClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final HandlePaymentCallback useCase =
            new HandlePaymentCallback(invoices, payments, resolver, events);

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), UUID.randomUUID());
    }

    private Payment pendingPaymentFor(Invoice invoice, String amount) {
        return new Payment(invoice, new BigDecimal(amount), BillingConstants.PaymentMethod.VNPAY, ORDER_ID,
                BillingConstants.PaymentStatus.PENDING);
    }

    private void stubClient() {
        when(resolver.resolve(PaymentGatewayType.VNPAY)).thenReturn(client);
    }

    private void stubVerifiedResult(boolean success, String amount) {
        stubClient();
        when(client.verifyCallback(anyMap())).thenReturn(
                new PaymentCallbackResult(ORDER_ID, success, new BigDecimal(amount), "ok"));
    }

    /** Chữ ký sai được dịch sang lỗi nghiệp vụ của billing - integrations.payment không được phụ
     * thuộc modules.billing nên nó chỉ ném PaymentSignatureException. */
    @Test
    void translatesAGatewaySignatureFailureIntoABillingException() {
        stubClient();
        when(client.verifyCallback(anyMap()))
                .thenThrow(new PaymentSignatureException(PaymentGatewayType.VNPAY));

        assertThatThrownBy(() -> useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS))
                .isInstanceOf(InvalidCallbackSignatureException.class);
        verify(payments, never()).findByGatewayTransactionId(any());
    }

    /** orderId lạ: bỏ qua im lặng, KHÔNG ném - nếu ném, kẻ tấn công dò được orderId nào tồn tại
     * (Review Focus #5, nửa ở tầng usecase). */
    @Test
    void ignoresAnUnknownOrderIdWithoutThrowing() {
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.empty());

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void creditsTheInvoiceAndPublishesPaymentReceivedOnSuccess() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        verify(events, times(1)).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    /** Final review Important #8: callback không có người gọi - actor phải là null, không phải người
     * đã tạo hoá đơn (họ không hề chạm vào giao dịch MoMo/VNPay này). Cùng chuẩn mà InvoiceOverdue đã
     * áp dụng cho sự kiện do hệ thống tự sinh. */
    @Test
    void publishesPaymentReceivedWithoutAnActorBecauseNoHumanTriggeredTheCallback() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        var published = org.mockito.ArgumentCaptor.forClass(BillingEvents.PaymentReceived.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().actorAccountId()).isNull();
        assertThat(published.getValue().invoiceId()).isEqualTo(invoice.getId());
        assertThat(published.getValue().actorBranchId()).isEqualTo(invoice.getBranchId());
    }

    /**
     * Final review Critical #2: một hoá đơn đã bị HUỶ (hoặc đã PAID qua đường khác - thu tay) trong
     * lúc một Payment PENDING của cổng vẫn còn treo. Callback đến sau, chữ ký hợp lệ, orderId khớp -
     * NHƯNG hoá đơn không còn nhận tiền được nữa. Trước bản vá, applySuccess chỉ kiểm tra
     * Payment.status mà không kiểm tra Invoice.status, nên hoá đơn đã huỷ bị "hồi sinh" thành PAID.
     */
    @Test
    void rejectsTheCreditWhenTheInvoiceIsNoLongerPayableAndLeavesItUntouched() {
        var invoice = invoiceOf("6000000");
        invoice.cancel();
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.CANCELLED);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.REJECTED);
        verify(events, never()).publishEvent(any());
    }

    /** Mặt khác của Critical #2: hoá đơn đã PAID qua thu tay trong lúc một Payment online khác vẫn
     * PENDING - callback thành công đến sau không được cộng tiền chồng lên mức đã PAID. */
    @Test
    void rejectsTheCreditWhenTheInvoiceIsAlreadyFullyPaidThroughAnotherChannel() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        invoice.applyPayment(new BigDecimal("6000000"));
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.REJECTED);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void leavesTheInvoicePartiallyPaidWhenTheInstallmentIsNotFullyCovered() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "2000000");
        stubVerifiedResult(true, "2000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("2000000"));
    }

    @Test
    void marksThePaymentFailedWithoutTouchingTheInvoiceWhenTheGatewayReportsFailure() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(false, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.FAILED);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        verify(events, never()).publishEvent(any());
    }

    /**
     * Review Focus #4: gọi callback thành công HAI lần với cùng {@code gatewayTransactionId} thì
     * {@code amountPaid} chỉ cộng MỘT lần, {@code Payment.status} lần thứ hai vẫn là {@code SUCCESS}
     * (không bị xử lý lại), và chỉ một event {@code PaymentReceived} được phát.
     */
    @Test
    void doesNotDoubleCreditWhenTheSameCallbackArrivesTwice() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);
        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        verify(events, times(1)).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    /** Mặt còn lại của idempotency: một callback THẤT BẠI gửi lại sau khi đã SUCCESS không được hạ
     * Payment về FAILED hay trừ tiền đã ghi nhận. */
    @Test
    void doesNotDowngradeASucceededPaymentWhenAFailureCallbackArrivesLater() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));
        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);
        when(client.verifyCallback(anyMap()))
                .thenReturn(new PaymentCallbackResult(ORDER_ID, false, new BigDecimal("6000000"), "late failure"));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
    }

    /** Số tiền ghi nhận là số đã lưu trong Payment (do chính hệ thống tính), không phải số cổng gửi
     * về - nếu tin số của cổng thì một callback giả mạo đúng chữ ký vẫn có thể ghi sai công nợ. */
    @Test
    void creditsTheAmountStoredOnThePaymentNotTheAmountReportedByTheGateway() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "2000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("2000000"));
    }
}
