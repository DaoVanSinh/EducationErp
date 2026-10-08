package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MarkOverdueInvoicesTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MarkOverdueInvoices useCase = new MarkOverdueInvoices(invoices, events);

    private Invoice overdueCandidate() {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("6000000"), LocalDate.of(2020, 1, 1), UUID.randomUUID());
    }

    @Test
    void marksEveryCandidateOverdueAndPublishesOneEventEach() {
        var first = overdueCandidate();
        var second = overdueCandidate();
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(first, second));

        var marked = useCase.execute();

        assertThat(marked).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.OVERDUE);
        assertThat(second.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.OVERDUE);
        verify(events, times(2)).publishEvent(any(BillingEvents.InvoiceOverdue.class));
    }

    /** Hệ thống tự sinh, không ai bấm - actorAccountId phải là null và audit phải ghi được như thế
     * (xem Task 23). */
    @Test
    void publishesInvoiceOverdueWithoutAnActorBecauseNoHumanTriggeredIt() {
        var invoice = overdueCandidate();
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(invoice));

        useCase.execute();

        var published = ArgumentCaptor.forClass(BillingEvents.InvoiceOverdue.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().invoiceId()).isEqualTo(invoice.getId());
        assertThat(published.getValue().actorAccountId()).isNull();
        assertThat(published.getValue().actorBranchId()).isEqualTo(invoice.getBranchId());
    }

    /** Chỉ quét UNPAID và PARTIALLY_PAID: PAID/CANCELLED/OVERDUE không còn là ứng viên, và việc
     * OVERDUE bị loại là lý do job này idempotent tự nhiên (lần chạy sau không thấy nữa). */
    @Test
    void scansOnlyUnpaidAndPartiallyPaidInvoices() {
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class))).thenReturn(List.of());

        useCase.execute();

        var statuses = ArgumentCaptor.forClass(List.class);
        var dueDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(invoices).findAllByStatusInAndDueDateBefore(statuses.capture(), dueDate.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(BillingConstants.InvoiceStatus.UNPAID,
                BillingConstants.InvoiceStatus.PARTIALLY_PAID);
        assertThat(dueDate.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void doesNothingWhenThereIsNoOverdueInvoice() {
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class))).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();
        verify(events, never()).publishEvent(any());
    }
}
