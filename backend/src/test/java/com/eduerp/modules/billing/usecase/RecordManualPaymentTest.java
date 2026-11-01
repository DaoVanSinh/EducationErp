package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidPaymentAmountException;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
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

class RecordManualPaymentTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecordManualPayment useCase = new RecordManualPayment(invoices, payments, events, mock(BillingBranchGuard.class));

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private static RecordManualPaymentRequest cash(String amount) {
        return new RecordManualPaymentRequest(new BigDecimal(amount));
    }

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), actorAccountId);
    }

    private Invoice stubbedInvoice(String amount) {
        var invoice = invoiceOf(amount);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return invoice;
    }

    private Payment savedPayment() {
        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, cash("1000000"), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void rejectsACancelledInvoice() {
        var invoice = stubbedInvoice("6000000");
        invoice.cancel();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), cash("1000000"), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectsAZeroOrNegativeAmount() {
        var invoice = stubbedInvoice("6000000");

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), cash("0"), actorAccountId, actorBranchId))
                .isInstanceOf(InvalidPaymentAmountException.class);
        assertThatThrownBy(() -> useCase.execute(invoice.getId(), cash("-1"), actorAccountId, actorBranchId))
                .isInstanceOf(InvalidPaymentAmountException.class);
    }

    /** Thu quá số còn lại bị chặn: tiền thừa phải xử lý ngoài hệ thống, không để amountPaid > amount. */
    @Test
    void rejectsAnAmountLargerThanWhatIsStillOwed() {
        var invoice = stubbedInvoice("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), cash("4000001"), actorAccountId, actorBranchId))
                .isInstanceOf(InvalidPaymentAmountException.class);
    }

    @Test
    void acceptsAnAmountExactlyEqualToWhatIsStillOwed() {
        var invoice = stubbedInvoice("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));

        var response = useCase.execute(invoice.getId(), cash("4000000"), actorAccountId, actorBranchId);

        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        assertThat(response.amountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
    }

    /** Không chọn phương thức = tiền mặt; người thu là tài khoản đang thao tác. */
    @Test
    void storesASucceededCashPaymentRecordedByTheActor() {
        var invoice = stubbedInvoice("6000000");

        useCase.execute(invoice.getId(), cash("2000000"), actorAccountId, actorBranchId);

        var payment = savedPayment();
        assertThat(payment.getMethod()).isEqualTo(BillingConstants.PaymentMethod.CASH);
        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getGatewayTransactionId()).isNull();
        assertThat(payment.getRecordedBy()).isEqualTo(actorAccountId);
        verify(events).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    @Test
    void storesABankTransferWithItsTrimmedNote() {
        var invoice = stubbedInvoice("6000000");
        var request = new RecordManualPaymentRequest(new BigDecimal("1500000"),
                BillingConstants.OfflinePaymentMethod.BANK_TRANSFER, "  CK VCB 123456  ");

        useCase.execute(invoice.getId(), request, actorAccountId, actorBranchId);

        var payment = savedPayment();
        assertThat(payment.getMethod()).isEqualTo(BillingConstants.PaymentMethod.BANK_TRANSFER);
        assertThat(payment.getNote()).isEqualTo("CK VCB 123456");
    }

    @Test
    void blankNoteIsStoredAsNull() {
        var invoice = stubbedInvoice("6000000");
        var request = new RecordManualPaymentRequest(new BigDecimal("1000000"), null, "   ");

        useCase.execute(invoice.getId(), request, actorAccountId, actorBranchId);

        var payment = savedPayment();
        assertThat(payment.getMethod()).isEqualTo(BillingConstants.PaymentMethod.CASH);
        assertThat(payment.getNote()).isNull();
    }

    /** Hoá đơn quá hạn vẫn thu được - OVERDUE chỉ là nhãn nhắc nợ. */
    @Test
    void acceptsAPaymentOnAnOverdueInvoice() {
        var invoice = stubbedInvoice("6000000");
        invoice.markOverdue();

        var response = useCase.execute(invoice.getId(), cash("6000000"), actorAccountId, actorBranchId);

        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }
}
