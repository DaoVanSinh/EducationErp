package com.eduerp.modules.billing.usecase;

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
    private final RecordManualPayment useCase = new RecordManualPayment(invoices, payments, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

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

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, new BigDecimal("1000000"), actorAccountId,
                actorBranchId)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void rejectsACancelledInvoice() {
        var invoice = stubbedInvoice("6000000");
        invoice.cancel();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), new BigDecimal("1000000"), actorAccountId,
                actorBranchId)).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectsAZeroOrNegativeAmount() {
        var invoice = stubbedInvoice("6000000");

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), BigDecimal.ZERO, actorAccountId, actorBranchId))
                .isInstanceOf(InvalidPaymentAmountException.class);
        assertThatThrownBy(() -> useCase.execute(invoice.getId(), new BigDecimal("-1"), actorAccountId,
                actorBranchId)).isInstanceOf(InvalidPaymentAmountException.class);
    }

    /** Thu quá số còn lại bị chặn: tiền thừa phải xử lý ngoài hệ thống, không để amountPaid > amount. */
    @Test
    void rejectsAnAmountLargerThanWhatIsStillOwed() {
        var invoice = stubbedInvoice("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), new BigDecimal("4000001"), actorAccountId,
                actorBranchId)).isInstanceOf(InvalidPaymentAmountException.class);
    }

    @Test
    void acceptsAnAmountExactlyEqualToWhatIsStillOwed() {
        var invoice = stubbedInvoice("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));

        var response = useCase.execute(invoice.getId(), new BigDecimal("4000000"), actorAccountId, actorBranchId);

        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        assertThat(response.amountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
    }

    @Test
    void storesASucceededManualPaymentWithoutAGatewayTransactionId() {
        var invoice = stubbedInvoice("6000000");

        useCase.execute(invoice.getId(), new BigDecimal("2000000"), actorAccountId, actorBranchId);

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getMethod()).isEqualTo(BillingConstants.PaymentMethod.MANUAL);
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(saved.getValue().getPaidAt()).isNotNull();
        assertThat(saved.getValue().getGatewayTransactionId()).isNull();
        verify(events).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    /** Hoá đơn quá hạn vẫn thu được - OVERDUE chỉ là nhãn nhắc nợ. */
    @Test
    void acceptsAPaymentOnAnOverdueInvoice() {
        var invoice = stubbedInvoice("6000000");
        invoice.markOverdue();

        var response = useCase.execute(invoice.getId(), new BigDecimal("6000000"), actorAccountId, actorBranchId);

        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }
}
