package com.eduerp.modules.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboHasInvoicesException;
import com.eduerp.modules.billing.EnrollmentAlreadyInvoicedException;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.pricing.EnrollmentDiscounts;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class TuitionInvoiceIssuerTest {

    private static final BigDecimal TUITION = new BigDecimal("6000000");
    private static final LocalDate START = LocalDate.of(2026, 12, 7);
    /** Khai giảng là ngày thứ nhất nên ngày thứ 7 là khai giảng cộng 6. */
    private static final LocalDate DUE = LocalDate.of(2026, 12, 13);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ComboRepository combos = mock(ComboRepository.class);
    private final EnrollmentDiscounts discounts = mock(EnrollmentDiscounts.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TuitionInvoiceIssuer issuer = new TuitionInvoiceIssuer(invoices, payments, combos, discounts, courses,
            enrollments, events);

    private final UUID actor = UUID.randomUUID();
    private final UUID actorBranch = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final EnrollmentManagement.EnrollmentSummaryResponse enrollment =
            new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentId, courseId, branchId,
                    EnrollmentConstants.EnrollmentStatus.ACTIVE, classId);

    @BeforeEach
    void stubCommonLookups() {
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));
        when(discounts.groupTuitionOf(any())).thenReturn(Optional.empty());
        when(courses.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, TUITION, true)));
        stubClassStart(classId, START);
    }

    private void stubClassStart(UUID id, LocalDate start) {
        when(courses.getClassInfo(id)).thenReturn(Optional.of(new CoursesManagement.ClassInfoResponse(id, courseId,
                branchId, UUID.randomUUID(), 20, 5, CoursesConstants.ClassStatus.ENROLLING, start, start.plusMonths(3),
                null, null, null, List.of())));
    }

    private Invoice invoice(int number, String amount, LocalDate due) {
        var invoice = new Invoice(enrollmentId, studentId, courseId, branchId, number, new BigDecimal(amount), due, actor);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        return invoice;
    }

    private void stubLive(Invoice... live) {
        when(invoices.findAllByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(List.of(live));
    }

    private Combo combo(String discounted, LocalDate due) {
        var combo = new Combo(studentId, branchId, new BigDecimal(discounted), BigDecimal.ZERO, due, actor);
        ReflectionTestUtils.setField(combo, "id", UUID.randomUUID());
        combo.addEnrollment(enrollmentId, courseId, new BigDecimal(discounted));
        return combo;
    }

    @Test
    void issuesFullTuitionDueOnTheSeventhDayFromTheClassStart() {
        stubLive();

        var issued = issuer.issueFor(enrollment, actor, actorBranch).orElseThrow();

        assertThat(issued.getAmount()).isEqualByComparingTo(TUITION);
        assertThat(issued.getDueDate()).isEqualTo(DUE);
        assertThat(issued.getInstallmentNumber()).isEqualTo(1);
        verify(events).publishEvent(any(BillingEvents.InvoiceCreated.class));
    }

    @Test
    void issuesTheGroupPriceForAGroupMember() {
        stubLive();
        when(discounts.groupTuitionOf(enrollmentId)).thenReturn(Optional.of(new BigDecimal("5100000")));

        var issued = issuer.issueFor(enrollment, actor, actorBranch).orElseThrow();

        assertThat(issued.getAmount()).isEqualByComparingTo("5100000");
    }

    /** Dữ liệu chia đợt cũ giữ nguyên: lập phần còn lại, số đợt là số nhỏ nhất còn trống. */
    @Test
    void issuesOnlyTheRemainderOfOldInstalments() {
        stubLive(invoice(1, "2000000", START));

        var issued = issuer.issueFor(enrollment, actor, actorBranch).orElseThrow();

        assertThat(issued.getAmount()).isEqualByComparingTo("4000000");
        assertThat(issued.getInstallmentNumber()).isEqualTo(2);
    }

    @Test
    void reusesTheNumberOfACancelledInstalment() {
        stubLive(invoice(2, "2000000", START));

        var issued = issuer.issueFor(enrollment, actor, actorBranch).orElseThrow();

        assertThat(issued.getInstallmentNumber()).isEqualTo(1);
    }

    @Test
    void issuesNothingWhenFullyInvoiced() {
        stubLive(invoice(1, "6000000", DUE));

        assertThat(issuer.issueFor(enrollment, actor, actorBranch)).isEmpty();
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void issuesNothingForAnEnrollmentInACombo() {
        when(discounts.isInCombo(enrollmentId)).thenReturn(true);

        assertThat(issuer.issueFor(enrollment, actor, actorBranch)).isEmpty();
    }

    /** Khoá chưa có giá không được chặn ghi danh. */
    @Test
    void issuesNothingWhenTheCourseHasNoTuition() {
        stubLive();
        when(courses.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, null, true)));

        assertThat(issuer.issueFor(enrollment, actor, actorBranch)).isEmpty();
    }

    @Test
    void cancelsUnpaidInvoicesAndFlushesBeforeReissuing() {
        var unpaid = invoice(1, "6000000", DUE);
        stubLive(unpaid);

        issuer.cancelUnpaidOf(enrollmentId, actor, actorBranch);

        assertThat(unpaid.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.CANCELLED);
        verify(invoices).flush();
        verify(events).publishEvent(any(BillingEvents.InvoiceCancelled.class));
    }

    @Test
    void refusesToReplaceAnInvoiceThatHasCollectedMoney() {
        var paid = invoice(1, "6000000", DUE);
        paid.applyPayment(new BigDecimal("1000000"));
        stubLive(paid);

        assertThatThrownBy(() -> issuer.cancelUnpaidOf(enrollmentId, actor, actorBranch))
                .isInstanceOf(EnrollmentAlreadyInvoicedException.class);
        assertThat(paid.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void refusesToReplaceAnInvoiceWithAPendingGatewayPayment() {
        var pending = invoice(1, "6000000", DUE);
        stubLive(pending);
        when(payments.existsByInvoice_IdAndStatus(pending.getId(), BillingConstants.PaymentStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> issuer.cancelUnpaidOf(enrollmentId, actor, actorBranch))
                .isInstanceOf(EnrollmentAlreadyInvoicedException.class);
    }

    @Test
    void withdrawalCancelsUnpaidButKeepsCollectedInvoices() {
        var unpaid = invoice(2, "3000000", DUE);
        var paid = invoice(1, "3000000", DUE);
        paid.applyPayment(new BigDecimal("3000000"));
        stubLive(paid, unpaid);
        when(combos.findByEnrollmentId(enrollmentId)).thenReturn(Optional.empty());

        issuer.settleWithdrawal(enrollmentId, actor, actorBranch);

        assertThat(unpaid.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.CANCELLED);
        assertThat(paid.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }

    /** Không được để học viên mang khoản nợ combo của lớp đã rút. */
    @Test
    void withdrawalDissolvesAnUncollectedCombo() {
        stubLive();
        var combo = combo("6000000", DUE);
        when(combos.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(combo));
        when(invoices.findAllByComboId(combo.getId())).thenReturn(List.of());
        when(enrollments.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentId, courseId, branchId,
                        EnrollmentConstants.EnrollmentStatus.WITHDRAWN, classId)));

        issuer.settleWithdrawal(enrollmentId, actor, actorBranch);

        verify(combos).delete(combo);
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void withdrawalKeepsAComboThatHasAPaymentTrail() {
        stubLive();
        var combo = combo("6000000", DUE);
        var comboInvoice = Invoice.forCombo(combo.getId(), studentId, branchId, 1, TUITION, DUE, actor);
        ReflectionTestUtils.setField(comboInvoice, "id", UUID.randomUUID());
        when(combos.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(combo));
        when(invoices.findAllByComboId(combo.getId())).thenReturn(List.of(comboInvoice));
        when(payments.existsByInvoice_Id(comboInvoice.getId())).thenReturn(true);

        issuer.settleWithdrawal(enrollmentId, actor, actorBranch);

        verify(combos, never()).delete(any(Combo.class));
    }

    @Test
    void dissolvingAComboDeletesItsInvoicesAndReissuesListPriceForActiveMembers() {
        stubLive();
        var combo = combo("5000000", DUE);
        var comboInvoice = Invoice.forCombo(combo.getId(), studentId, branchId, 1, new BigDecimal("5000000"), DUE, actor);
        ReflectionTestUtils.setField(comboInvoice, "id", UUID.randomUUID());
        when(invoices.findAllByComboId(combo.getId())).thenReturn(List.of(comboInvoice));
        when(enrollments.getEnrollment(enrollmentId)).thenReturn(Optional.of(enrollment));

        issuer.dissolveCombo(combo, actor, actorBranch);

        verify(invoices).deleteAll(List.of(comboInvoice));
        verify(combos).delete(combo);
        var issued = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).saveAndFlush(issued.capture());
        assertThat(issued.getValue().getAmount()).isEqualByComparingTo(TUITION);
        assertThat(issued.getValue().getEnrollmentId()).isEqualTo(enrollmentId);
    }

    @Test
    void refusesToDissolveAComboWithAPaymentTrail() {
        var combo = combo("5000000", DUE);
        var comboInvoice = Invoice.forCombo(combo.getId(), studentId, branchId, 1, new BigDecimal("5000000"), DUE, actor);
        comboInvoice.applyPayment(new BigDecimal("5000000"));
        when(invoices.findAllByComboId(combo.getId())).thenReturn(List.of(comboInvoice));

        assertThatThrownBy(() -> issuer.dissolveCombo(combo, actor, actorBranch))
                .isInstanceOf(ComboHasInvoicesException.class);
        verify(combos, never()).delete(any(Combo.class));
    }

    @Test
    void comboDueDateFollowsTheEarliestClass() {
        var laterClass = UUID.randomUUID();
        stubClassStart(laterClass, START.plusDays(30));

        assertThat(issuer.earliestDueDateOf(List.of(laterClass, classId))).isEqualTo(DUE);
    }

    @Test
    void comboInvoiceIsTheRemainingDiscountedTotalInOnePayment() {
        var combo = combo("9000000", DUE);
        var old = Invoice.forCombo(combo.getId(), studentId, branchId, 1, new BigDecimal("4000000"), DUE, actor);
        when(invoices.findAllByComboIdAndStatusNot(combo.getId(), BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(List.of(old));

        var issued = issuer.issueForCombo(combo, actor, actorBranch).orElseThrow();

        assertThat(issued.getAmount()).isEqualByComparingTo("5000000");
        assertThat(issued.getInstallmentNumber()).isEqualTo(2);
        assertThat(issued.getDueDate()).isEqualTo(DUE);
    }

    private void stubRoster() {
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of(new EnrollmentManagement.ClassRosterEntry(
                enrollmentId, studentId, courseId, EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
        when(combos.findByEnrollmentId(enrollmentId)).thenReturn(Optional.empty());
    }

    @Test
    void movingTheClassStartMovesTheDueDateAndClearsOverdue() {
        var overdue = invoice(1, "6000000", DUE);
        overdue.markOverdue();
        stubLive(overdue);
        stubRoster();

        issuer.rescheduleForClass(classId, START, START.plusDays(10));

        assertThat(overdue.getDueDate()).isEqualTo(DUE.plusDays(10));
        assertThat(overdue.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
    }

    @Test
    void movingTheClassStartKeepsAnExtendedDueDate() {
        var extended = invoice(1, "6000000", DUE);
        extended.extendDue(DUE, "Gia đình xin thêm thời gian");
        stubLive(extended);
        stubRoster();

        issuer.rescheduleForClass(classId, START, START.plusDays(10));

        assertThat(extended.getDueDate()).isEqualTo(DUE);
    }

    /** Hoá đơn chia đợt cũ có hạn riêng: giữ nguyên dữ liệu cũ. */
    @Test
    void movingTheClassStartKeepsOldInstalmentDueDates() {
        var oldInstalment = invoice(2, "3000000", DUE.plusDays(30));
        stubLive(oldInstalment);
        stubRoster();

        issuer.rescheduleForClass(classId, START, START.plusDays(10));

        assertThat(oldInstalment.getDueDate()).isEqualTo(DUE.plusDays(30));
    }

    @Test
    void movingTheClassStartMovesAComboDueDate() {
        stubLive();
        var combo = combo("9000000", DUE);
        var comboInvoice = Invoice.forCombo(combo.getId(), studentId, branchId, 1, new BigDecimal("9000000"), DUE, actor);
        stubRoster();
        when(combos.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(combo));
        when(enrollments.getEnrollment(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(invoices.findAllByComboIdAndStatusNot(eq(combo.getId()), eq(BillingConstants.InvoiceStatus.CANCELLED)))
                .thenReturn(List.of(comboInvoice));
        stubClassStart(classId, START.plusDays(10));

        issuer.rescheduleForClass(classId, START, START.plusDays(10));

        assertThat(combo.getDueDate()).isEqualTo(DUE.plusDays(10));
        assertThat(comboInvoice.getDueDate()).isEqualTo(DUE.plusDays(10));
    }
}
