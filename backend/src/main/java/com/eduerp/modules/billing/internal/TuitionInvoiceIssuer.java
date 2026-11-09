package com.eduerp.modules.billing.internal;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboHasInvoicesException;
import com.eduerp.modules.billing.EnrollmentAlreadyInvoicedException;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.pricing.EnrollmentDiscounts;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Mỗi ghi danh một hoá đơn, thu đủ một lần, hạn là ngày thứ 7 tính từ khai giảng. Một chỗ duy nhất giữ luật này cho
 * lập tự động khi ghi danh, lập lại sau khi huỷ, và đổi hoá đơn khi vào hoặc ra khỏi combo, đăng ký nhóm.
 *
 * <p>Số tiền lập là phần chưa có hoá đơn sống, không phải luôn đủ học phí: dữ liệu chia đợt cũ vẫn giữ nguyên, phần còn
 * lại của nó được thu đủ một lần.
 */
@Component
public class TuitionInvoiceIssuer {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ComboRepository combos;
    private final EnrollmentDiscounts discounts;
    private final CoursesManagement courses;
    private final EnrollmentManagement enrollments;
    private final ApplicationEventPublisher events;

    TuitionInvoiceIssuer(InvoiceRepository invoices, PaymentRepository payments, ComboRepository combos,
            EnrollmentDiscounts discounts, CoursesManagement courses, EnrollmentManagement enrollments,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.combos = combos;
        this.discounts = discounts;
        this.courses = courses;
        this.enrollments = enrollments;
        this.events = events;
    }

    /**
     * Rỗng khi không có gì để lập: đã nằm trong combo, đã có đủ hoá đơn, hoặc khoá chưa có học phí. Trường hợp cuối ghi
     * danh vẫn thành công, kế toán lập lại sau khi khoá có giá.
     */
    public Optional<Invoice> issueFor(EnrollmentManagement.EnrollmentSummaryResponse enrollment, UUID actorAccountId,
            UUID actorBranchId) {
        if (discounts.isInCombo(enrollment.enrollmentId())) {
            return Optional.empty();
        }
        var live = liveOf(enrollment.enrollmentId());
        return tuitionOf(enrollment).map(tuition -> tuition.subtract(totalOf(live)))
                .filter(amount -> amount.signum() > 0)
                .map(amount -> published(new Invoice(enrollment.enrollmentId(), enrollment.studentProfileId(),
                        enrollment.courseId(), enrollment.branchId(), nextInstallment(live), amount,
                        dueDateOf(enrollment.classId()), actorAccountId), actorAccountId, actorBranchId));
    }

    /** Rỗng khi combo đã có hoá đơn sống đủ số tiền sau giảm giá. */
    public Optional<Invoice> issueForCombo(Combo combo, UUID actorAccountId, UUID actorBranchId) {
        var live = invoices.findAllByComboIdAndStatusNot(combo.getId(), BillingConstants.InvoiceStatus.CANCELLED);
        var amount = combo.getTotalDiscountedAmount().subtract(totalOf(live));
        if (amount.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(published(Invoice.forCombo(combo.getId(), combo.getStudentProfileId(), combo.getBranchId(),
                nextInstallment(live), amount, combo.getDueDate(), actorAccountId), actorAccountId, actorBranchId));
    }

    public void reissueIfActive(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        enrollments.getEnrollment(enrollmentId)
                .filter(enrollment -> enrollment.status() == EnrollmentConstants.EnrollmentStatus.ACTIVE)
                .ifPresent(enrollment -> issueFor(enrollment, actorAccountId, actorBranchId));
    }

    /** Hạn của combo tính theo lớp khai giảng sớm nhất trong gói. */
    public LocalDate earliestDueDateOf(Collection<UUID> classIds) {
        return classIds.stream().map(this::dueDateOf).min(LocalDate::compareTo).orElseThrow();
    }

    /** Đã thu tiền hoặc đang có giao dịch chờ cổng thanh toán: không tự đổi hoá đơn được nữa. */
    public boolean hasCollected(UUID enrollmentId) {
        return liveOf(enrollmentId).stream().anyMatch(this::hasMoneyOrPendingPayment);
    }

    /** Thay hoá đơn khi đổi giá. Flush ngay để hoá đơn lập lại không va chỉ mục duy nhất của hoá đơn vừa huỷ. */
    public void cancelUnpaidOf(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        if (hasCollected(enrollmentId)) {
            throw new EnrollmentAlreadyInvoicedException(enrollmentId);
        }
        liveOf(enrollmentId).forEach(invoice -> cancel(invoice, actorAccountId, actorBranchId));
        invoices.flush();
    }

    /**
     * Rút lớp: huỷ phần chưa thu, hoá đơn đã thu giữ nguyên để đối soát hoàn phí. Ghi danh trong combo chưa thu thì giải
     * thể combo, vì giá đã giảm không còn đúng và không được để học viên mang khoản nợ của lớp đã rút.
     */
    public void settleWithdrawal(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        liveOf(enrollmentId).stream().filter(invoice -> !hasMoneyOrPendingPayment(invoice))
                .forEach(invoice -> cancel(invoice, actorAccountId, actorBranchId));
        combos.findByEnrollmentId(enrollmentId)
                .filter(combo -> invoices.findAllByComboId(combo.getId()).stream().noneMatch(this::hasPaymentTrail))
                .ifPresent(combo -> dissolveCombo(combo, actorAccountId, actorBranchId));
    }

    /**
     * Xoá cứng combo cùng hoá đơn của nó rồi lập lại hoá đơn giá thường cho các ghi danh còn học. Chỉ khi chưa có giao
     * dịch nào, vì dấu vết thu tiền không được mất. Khoá ngoại invoices.combo_id buộc phải xoá hoá đơn trước combo.
     */
    public void dissolveCombo(Combo combo, UUID actorAccountId, UUID actorBranchId) {
        var comboInvoices = invoices.findAllByComboId(combo.getId());
        if (comboInvoices.stream().anyMatch(this::hasPaymentTrail)) {
            throw new ComboHasInvoicesException(combo.getId());
        }
        var memberIds = combo.getEnrollments().stream().map(ComboEnrollment::getEnrollmentId).toList();
        comboInvoices.forEach(invoice -> events.publishEvent(
                new BillingEvents.InvoiceCancelled(invoice.getId(), actorAccountId, actorBranchId)));
        invoices.deleteAll(comboInvoices);
        invoices.flush();
        // Phát trước khi xoá: sau lệnh delete đây là dấu vết duy nhất cho thấy combo từng tồn tại.
        events.publishEvent(new BillingEvents.ComboCancelled(combo.getId(), actorAccountId, actorBranchId));
        combos.delete(combo);
        combos.flush();
        memberIds.forEach(id -> reissueIfActive(id, actorAccountId, actorBranchId));
    }

    /**
     * Đổi ngày khai giảng thì hạn đổi theo. Chỉ hoá đơn đang đúng hạn theo ngày cũ: hoá đơn đã được gia hạn và hoá đơn
     * chia đợt cũ có hạn riêng thì giữ nguyên.
     */
    public void rescheduleForClass(UUID classId, LocalDate oldStartDate, LocalDate newStartDate) {
        var oldDue = BillingRules.tuitionDueDate(oldStartDate);
        var newDue = BillingRules.tuitionDueDate(newStartDate);
        enrollments.listEnrollmentsByClass(classId).stream()
                .filter(entry -> entry.status() == EnrollmentConstants.EnrollmentStatus.ACTIVE)
                .forEach(entry -> {
                    liveOf(entry.enrollmentId()).stream().filter(invoice -> reschedulable(invoice, oldDue))
                            .forEach(invoice -> invoice.rescheduleDue(newDue));
                    combos.findByEnrollmentId(entry.enrollmentId()).ifPresent(this::rescheduleCombo);
                });
    }

    private void rescheduleCombo(Combo combo) {
        var oldDue = combo.getDueDate();
        var newDue = earliestDueDateOf(combo.getEnrollments().stream()
                .map(member -> enrollments.getEnrollment(member.getEnrollmentId()))
                .flatMap(Optional::stream)
                .map(EnrollmentManagement.EnrollmentSummaryResponse::classId)
                .toList());
        invoices.findAllByComboIdAndStatusNot(combo.getId(), BillingConstants.InvoiceStatus.CANCELLED).stream()
                .filter(invoice -> reschedulable(invoice, oldDue))
                .forEach(invoice -> invoice.rescheduleDue(newDue));
        combo.rescheduleDue(newDue);
    }

    private static boolean reschedulable(Invoice invoice, LocalDate expectedDue) {
        return !invoice.isDueExtended() && BillingRules.isPayable(invoice.getStatus())
                && invoice.getDueDate().equals(expectedDue);
    }

    private Invoice published(Invoice invoice, UUID actorAccountId, UUID actorBranchId) {
        var saved = invoices.saveAndFlush(invoice);
        events.publishEvent(new BillingEvents.InvoiceCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved;
    }

    private void cancel(Invoice invoice, UUID actorAccountId, UUID actorBranchId) {
        invoice.cancel();
        events.publishEvent(new BillingEvents.InvoiceCancelled(invoice.getId(), actorAccountId, actorBranchId));
    }

    private List<Invoice> liveOf(UUID enrollmentId) {
        return invoices.findAllByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED);
    }

    private boolean hasMoneyOrPendingPayment(Invoice invoice) {
        return invoice.getAmountPaid().signum() > 0 || payments.existsByInvoice_IdAndStatus(invoice.getId(),
                BillingConstants.PaymentStatus.PENDING);
    }

    private boolean hasPaymentTrail(Invoice invoice) {
        return invoice.getAmountPaid().signum() > 0 || payments.existsByInvoice_Id(invoice.getId());
    }

    private LocalDate dueDateOf(UUID classId) {
        return BillingRules.tuitionDueDate(courses.getClassInfo(classId).orElseThrow().startDate());
    }

    private Optional<BigDecimal> tuitionOf(EnrollmentManagement.EnrollmentSummaryResponse enrollment) {
        return discounts.groupTuitionOf(enrollment.enrollmentId()).or(() -> courses
                .getCourseTuition(enrollment.courseId()).map(CoursesManagement.CourseTuitionResponse::tuitionFee));
    }

    private static BigDecimal totalOf(List<Invoice> invoices) {
        return invoices.stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Số nhỏ nhất còn trống: một đợt đã huỷ giải phóng đúng số của nó, đếm rồi cộng một có thể trùng đợt còn sống. */
    private static int nextInstallment(List<Invoice> live) {
        var used = live.stream().map(Invoice::getInstallmentNumber).collect(Collectors.toSet());
        var candidate = 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }
}
