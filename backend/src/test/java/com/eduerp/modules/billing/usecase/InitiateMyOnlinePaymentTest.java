package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.internal.OnlinePaymentStarter;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InitiateMyOnlinePaymentTest {

    private final StudentsManagement students = mock(StudentsManagement.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
    private final PaymentGatewayClient client = mock(PaymentGatewayClient.class);
    private final InitiateMyOnlinePayment useCase =
            new InitiateMyOnlinePayment(students, invoices, new OnlinePaymentStarter(payments, resolver));

    private final UUID accountId = UUID.randomUUID();
    private final UUID myProfileId = UUID.randomUUID();

    private Invoice invoiceOwnedBy(UUID studentProfileId) {
        var invoice = new Invoice(UUID.randomUUID(), studentProfileId, UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("3000000"), LocalDate.of(2026, 12, 15), UUID.randomUUID());
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        return invoice;
    }

    private void signedInAsStudent() {
        when(students.findProfileByAccountId(accountId))
                .thenReturn(Optional.of(new StudentsManagement.StudentSummaryResponse(myProfileId, accountId, true)));
    }

    @Test
    void createsAVnpayLinkForTheRemainingAmountOfMyOwnInvoice() {
        signedInAsStudent();
        var invoice = invoiceOwnedBy(myProfileId);
        invoice.applyPayment(new BigDecimal("1000000"));
        when(resolver.resolve(BillingConstants.PaymentMethod.VNPAY)).thenReturn(client);
        when(client.createPaymentUrl(any())).thenAnswer(call -> new PaymentUrlResult("https://pay.example/vnpay",
                call.<com.eduerp.integrations.payment.PaymentRequest>getArgument(0).orderId()));

        var response = useCase.execute(accountId, invoice.getId(), BillingConstants.PaymentMethod.VNPAY);

        assertThat(response.payUrl()).isEqualTo("https://pay.example/vnpay");
        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("2000000"));
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
    }

    /** Hoá đơn của học viên khác trả "không tìm thấy" - không lộ là nó có tồn tại. */
    @Test
    void refusesSomeoneElsesInvoiceAsIfItDidNotExist() {
        signedInAsStudent();
        var othersInvoice = invoiceOwnedBy(UUID.randomUUID());

        assertThatThrownBy(() -> useCase.execute(accountId, othersInvoice.getId(),
                BillingConstants.PaymentMethod.VNPAY)).isInstanceOf(InvoiceNotFoundException.class);
        verify(payments, never()).save(any());
    }

    @Test
    void refusesAnAccountWithoutAStudentProfile() {
        when(students.findProfileByAccountId(accountId)).thenReturn(Optional.empty());
        var invoice = invoiceOwnedBy(myProfileId);

        assertThatThrownBy(() -> useCase.execute(accountId, invoice.getId(), BillingConstants.PaymentMethod.VNPAY))
                .isInstanceOf(InvoiceNotFoundException.class);
        verify(payments, never()).save(any());
    }
}
