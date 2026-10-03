package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** {@code invoice} là quan hệ JPA thật - {@code Invoice} cùng module (rule #3 chỉ cấm xuyên module),
 * mirror {@code Payslip.payrollRun}. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", updatable = false)
    private Invoice invoice;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BillingConstants.PaymentMethod method;

    /** Mã giao dịch của cổng, null với MANUAL. UNIQUE ở DB nên hai callback cùng mã không thể tạo
     * hai bản ghi (Review Focus #4, lớp phòng thủ DB). */
    @Column(name = "gateway_transaction_id", length = 128, updatable = false)
    private String gatewayTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingConstants.PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Payment(Invoice invoice, BigDecimal amount, BillingConstants.PaymentMethod method,
            String gatewayTransactionId, BillingConstants.PaymentStatus status) {
        this.invoice = invoice;
        this.amount = BillingRules.money(amount);
        this.method = method;
        this.gatewayTransactionId = gatewayTransactionId;
        this.status = status;
        this.createdAt = Instant.now();
        if (status == BillingConstants.PaymentStatus.SUCCESS) {
            this.paidAt = this.createdAt;
        }
    }

    public void markSucceeded() {
        this.status = BillingConstants.PaymentStatus.SUCCESS;
        this.paidAt = Instant.now();
    }

    public void markFailed() {
        this.status = BillingConstants.PaymentStatus.FAILED;
    }
}
