package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một gói nhiều khoá của CÙNG một học viên, thu như MỘT đơn vị: một tổng tiền sau giảm, một hạn
 * đóng, tối đa 3 đợt cho cả combo (spec mục 2).
 *
 * <p>Không có cột {@code status}: combo chỉ tồn tại khi chưa bị huỷ, và huỷ chỉ được phép trước khi
 * phát hành hoá đơn đầu tiên - lúc đó xoá cứng cả combo lẫn {@code ComboEnrollment} của nó. Một khi
 * đã có hoá đơn, combo là chứng từ tài chính đã chốt, không xoá được nữa (spec mục 4).
 *
 * <p>Mọi số tiền và % giảm là SNAPSHOT lúc tạo: đổi học phí khoá hay đổi bậc giảm giá về sau không
 * được làm đổi một combo đã chốt.
 */
@Entity
@Table(name = "combos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Combo {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "total_original_amount", nullable = false, updatable = false)
    private BigDecimal totalOriginalAmount;

    @Column(name = "discount_percent", nullable = false, updatable = false)
    private BigDecimal discountPercent;

    @Column(name = "total_discounted_amount", nullable = false, updatable = false)
    private BigDecimal totalDiscountedAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    // Gộp lazy-load của nhiều Combo thành một câu IN duy nhất khi liệt kê một trang - không batch
    // thì mỗi combo trong trang tự bắn một SELECT riêng để đếm số khoá (N+1), xem ListCombos.
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("originalTuitionFee DESC")
    @BatchSize(size = 50)
    private final List<ComboEnrollment> enrollments = new ArrayList<>();

    public Combo(UUID studentProfileId, UUID branchId, BigDecimal totalOriginalAmount,
            BigDecimal discountPercent, LocalDate dueDate, UUID createdByAccountId) {
        this.studentProfileId = studentProfileId;
        this.branchId = branchId;
        this.totalOriginalAmount = BillingRules.money(totalOriginalAmount);
        this.discountPercent = BillingRules.percent(discountPercent);
        // Tính trong constructor, không nhận từ ngoài: không để ai lưu được một combo mà tổng sau
        // giảm không khớp với tổng gốc và % giảm của chính nó.
        this.totalDiscountedAmount = BillingRules.discountedTotal(totalOriginalAmount, discountPercent);
        this.dueDate = dueDate;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = Instant.now();
    }

    public List<ComboEnrollment> getEnrollments() {
        return List.copyOf(enrollments);
    }

    public void addEnrollment(UUID enrollmentId, UUID courseId, BigDecimal originalTuitionFee) {
        enrollments.add(new ComboEnrollment(this, enrollmentId, courseId, originalTuitionFee));
    }
}
