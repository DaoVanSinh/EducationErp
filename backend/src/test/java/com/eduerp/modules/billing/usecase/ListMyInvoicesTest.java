package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** Cổng học phí: chỉ hoá đơn của hồ sơ gắn với chính tài khoản đăng nhập, kèm lịch sử thanh toán. */
class ListMyInvoicesTest {

    private final StudentsManagement students = mock(StudentsManagement.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ListMyInvoices useCase = new ListMyInvoices(students, invoices, payments);

    private final UUID accountId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    private Invoice invoice(Instant issuedAt) {
        var invoice = new Invoice(UUID.randomUUID(), profileId, UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("1000000"), LocalDate.now().plusDays(7), UUID.randomUUID());
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(invoice, "issuedAt", issuedAt);
        return invoice;
    }

    @Test
    void returnsNothingForAnAccountWithoutAStudentProfile() {
        when(students.findProfileByAccountId(accountId)).thenReturn(Optional.empty());

        assertThat(useCase.execute(accountId)).isEmpty();
        verifyNoInteractions(invoices, payments);
    }

    @Test
    void listsOnlyTheCallersInvoicesNewestFirstWithTheirPayments() {
        when(students.findProfileByAccountId(accountId))
                .thenReturn(Optional.of(new StudentsManagement.StudentSummaryResponse(profileId, accountId, true)));
        var older = invoice(Instant.parse("2026-09-01T00:00:00Z"));
        var newer = invoice(Instant.parse("2026-10-01T00:00:00Z"));
        when(invoices.search(eq(profileId), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(older, newer)));
        var payment = new Payment(older, new BigDecimal("400000"), BillingConstants.PaymentMethod.MANUAL, null,
                BillingConstants.PaymentStatus.SUCCESS);
        when(payments.findAllByInvoice_IdInOrderByCreatedAtDesc(anyCollection())).thenReturn(List.of(payment));

        var result = useCase.execute(accountId);

        assertThat(result).extracting(detail -> detail.invoice().id()).containsExactly(newer.getId(), older.getId());
        assertThat(result.get(0).payments()).isEmpty();
        assertThat(result.get(1).payments()).hasSize(1);
        assertThat(result.get(1).payments().get(0).amount()).isEqualByComparingTo("400000");
    }
}
