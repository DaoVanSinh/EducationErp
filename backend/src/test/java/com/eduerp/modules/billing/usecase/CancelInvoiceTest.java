package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvoiceHasPendingPaymentException;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CancelInvoiceTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CancelInvoice useCase = new CancelInvoice(invoices, payments, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Invoice stubbedInvoice() {
        var invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("6000000"), LocalDate.of(2026, 11, 30), actorAccountId);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.existsByInvoice_IdAndStatus(invoice.getId(), BillingConstants.PaymentStatus.PENDING))
                .thenReturn(false);
        return invoice;
    }

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void cancelsAnInvoiceThatHasNotReceivedAnyMoney() {
        var invoice = stubbedInvoice();

        useCase.execute(invoice.getId(), actorAccountId, actorBranchId);

        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.CANCELLED);
    }

    /** Đã có tiền vào thì phải hoàn tiền ngoài hệ thống trước, không được huỷ (spec mục 5). */
    @Test
    void refusesToCancelAPartiallyPaidInvoice() {
        var invoice = stubbedInvoice();
        invoice.applyPayment(new BigDecimal("1000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void refusesToCancelAPaidInvoice() {
        var invoice = stubbedInvoice();
        invoice.applyPayment(new BigDecimal("6000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    /** OVERDUE cũng không huỷ được: chỉ UNPAID mới huỷ - một hoá đơn quá hạn là công nợ thật đang
     * chờ thu, huỷ nó là xoá nợ, không phải sửa lỗi nhập liệu. */
    @Test
    void refusesToCancelAnOverdueInvoice() {
        var invoice = stubbedInvoice();
        invoice.markOverdue();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void refusesToCancelTwice() {
        var invoice = stubbedInvoice();
        invoice.cancel();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    /**
     * Final review Important #9: huỷ hoá đơn trước bản vá không để lại dấu vết nào - không event,
     * không audit. Voiding một chứng từ tài chính mà không ghi lại ai đã làm là dưới chuẩn một hệ
     * thống thu tiền nên có. Thêm {@code InvoiceCancelled}, mirror đúng hình dạng 3 event còn lại.
     */
    @Test
    void publishesInvoiceCancelledWithTheActorWhoCancelledIt() {
        var invoice = stubbedInvoice();

        useCase.execute(invoice.getId(), actorAccountId, actorBranchId);

        var published = org.mockito.ArgumentCaptor.forClass(BillingEvents.InvoiceCancelled.class);
        verify(events, times(1)).publishEvent(published.capture());
        assertThat(published.getValue().invoiceId()).isEqualTo(invoice.getId());
        assertThat(published.getValue().actorAccountId()).isEqualTo(actorAccountId);
        assertThat(published.getValue().actorBranchId()).isEqualTo(actorBranchId);
    }

    @Test
    void publishesNoEventWhenCancellationIsRefused() {
        var invoice = stubbedInvoice();
        invoice.applyPayment(new BigDecimal("1000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);

        verify(events, never()).publishEvent(any());
    }

    /**
     * Final review Critical #2, nửa còn lại: huỷ một hoá đơn còn một giao dịch online PENDING mở
     * đường cho callback đến muộn hồi sinh hoá đơn đã huỷ (xem
     * HandlePaymentCallbackTest.rejectsTheCreditWhenTheInvoiceIsNoLongerPayable...). Chặn ngay ở đây
     * để kế toán biết lý do, thay vì để callback âm thầm REJECTED về sau.
     */
    @Test
    void refusesToCancelWhileAnOnlinePaymentIsStillPending() {
        var invoice = stubbedInvoice();
        when(payments.existsByInvoice_IdAndStatus(invoice.getId(), BillingConstants.PaymentStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceHasPendingPaymentException.class);
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
    }
}
