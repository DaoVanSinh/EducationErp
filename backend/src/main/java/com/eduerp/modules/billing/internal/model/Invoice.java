package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Một đợt thu học phí. Mọi chuyển trạng thái đi qua method của chính entity - không setter trần cho
 * {@code status}/{@code amountPaid}, để không ai cộng tiền mà quên cập nhật trạng thái. */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingConstants.InvoiceStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    public Invoice(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber,
            BigDecimal amount, LocalDate dueDate, UUID createdByAccountId) {
        this.enrollmentId = enrollmentId;
        this.studentProfileId = studentProfileId;
        this.courseId = courseId;
        this.branchId = branchId;
        this.installmentNumber = installmentNumber;
        this.amount = BillingRules.money(amount);
        this.dueDate = dueDate;
        this.createdByAccountId = createdByAccountId;
        this.amountPaid = BillingRules.money(BigDecimal.ZERO);
        this.status = BillingConstants.InvoiceStatus.UNPAID;
        this.issuedAt = Instant.now();
    }

    /** Cộng tiền và chuyển trạng thái trong cùng một lệnh - không thể cộng mà quên đổi status. */
    public void applyPayment(BigDecimal paidAmount) {
        this.amountPaid = BillingRules.money(this.amountPaid.add(paidAmount));
        this.status = BillingRules.statusAfterPayment(this.amount, this.amountPaid);
    }

    public void markOverdue() {
        this.status = BillingConstants.InvoiceStatus.OVERDUE;
    }

    public void cancel() {
        this.status = BillingConstants.InvoiceStatus.CANCELLED;
    }
}
