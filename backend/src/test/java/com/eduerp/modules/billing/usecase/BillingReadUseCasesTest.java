package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.PaymentNotFoundException;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class BillingReadUseCasesTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ListInvoices listInvoices = new ListInvoices(invoices);
    private final GetInvoiceDetail getInvoiceDetail = new GetInvoiceDetail(invoices, payments);
    private final GetPaymentStatus getPaymentStatus = new GetPaymentStatus(payments);

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), UUID.randomUUID());
    }

    @Test
    void listPassesAllThreeOptionalFiltersStraightToTheRepository() {
        var invoice = invoiceOf("6000000");
        var pageable = PageRequest.of(0, 20);
        when(invoices.search(invoice.getStudentProfileId(), invoice.getEnrollmentId(),
                BillingConstants.InvoiceStatus.UNPAID, pageable))
                .thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

        var page = listInvoices.execute(pageable, invoice.getStudentProfileId(), invoice.getEnrollmentId(),
                BillingConstants.InvoiceStatus.UNPAID);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(invoice.getId()));
    }

    @Test
    void listAcceptsAllNullFilters() {
        var pageable = PageRequest.of(0, 20);
        when(invoices.search(null, null, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(listInvoices.execute(pageable, null, null, null).items()).isEmpty();
    }

    @Test
    void detailReturnsTheInvoiceWithItsPaymentHistory() {
        var invoice = invoiceOf("6000000");
        var manual = new Payment(invoice, new BigDecimal("2000000"), BillingConstants.PaymentMethod.MANUAL, null,
                BillingConstants.PaymentStatus.SUCCESS);
        var pending = new Payment(invoice, new BigDecimal("4000000"), BillingConstants.PaymentMethod.MOMO,
                "order-1", BillingConstants.PaymentStatus.PENDING);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoice.getId()))
                .thenReturn(List.of(pending, manual));

        var detail = getInvoiceDetail.execute(invoice.getId());

        assertThat(detail.invoice().id()).isEqualTo(invoice.getId());
        assertThat(detail.payments()).hasSize(2);
        // Thứ tự giữ nguyên thứ tự repository trả về (createdAt DESC) - không sort lại ở usecase.
        assertThat(detail.payments().get(0).method()).isEqualTo(BillingConstants.PaymentMethod.MOMO);
        assertThat(detail.payments().get(0).status()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
        assertThat(detail.payments().get(0).paidAt()).isNull();
        assertThat(detail.payments().get(1).method()).isEqualTo(BillingConstants.PaymentMethod.MANUAL);
        assertThat(detail.payments().get(1).paidAt()).isNotNull();
    }

    @Test
    void detailRejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getInvoiceDetail.execute(invoiceId)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void paymentStatusReturnsTheTransactionWithoutLeakingItsGatewayId() {
        var invoice = invoiceOf("6000000");
        var payment = new Payment(invoice, new BigDecimal("6000000"), BillingConstants.PaymentMethod.VNPAY,
                "order-1", BillingConstants.PaymentStatus.PENDING);
        when(payments.findByGatewayTransactionId("order-1")).thenReturn(Optional.of(payment));

        var response = getPaymentStatus.execute("order-1");

        assertThat(response.status()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo(BillingConstants.PaymentMethod.VNPAY);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("6000000"));
        // PaymentResponse cố ý không có field gatewayTransactionId - endpoint này là public.
        assertThat(PaymentResponseFields.names()).doesNotContain("gatewayTransactionId");
    }

    @Test
    void paymentStatusRejectsAnUnknownTransaction() {
        when(payments.findByGatewayTransactionId("order-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getPaymentStatus.execute("order-unknown"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    /** Chốt hợp đồng của PaymentResponse bằng reflection: nếu ai thêm field nhạy cảm vào record này
     * thì test đỏ, vì payload đó đi ra một endpoint không cần đăng nhập. */
    private static final class PaymentResponseFields {
        private PaymentResponseFields() {
        }

        static List<String> names() {
            return java.util.Arrays
                    .stream(com.eduerp.modules.billing.dto.PaymentResponse.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList();
        }
    }
}
