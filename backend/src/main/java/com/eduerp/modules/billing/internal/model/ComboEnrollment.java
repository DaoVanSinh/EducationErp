package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một khoá trong combo. {@code combo} là quan hệ JPA thật - {@code Combo} cùng module (rule #3 chỉ
 * cấm xuyên module), mirror {@code ClassSchedule.parentClass}. {@code enrollmentId}/{@code courseId}
 * là UUID trần vì {@code enrollment}/{@code courses} là module khác.
 */
@Entity
@Table(name = "combo_enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComboEnrollment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    /** Snapshot {@code CourseTuitionResponse.tuitionFee()} lúc tạo combo - đổi giá khoá học về sau
     * không được làm đổi một combo đã chốt (spec mục 4). */
    @Column(name = "original_tuition_fee", nullable = false, updatable = false)
    private BigDecimal originalTuitionFee;

    ComboEnrollment(Combo combo, UUID enrollmentId, UUID courseId, BigDecimal originalTuitionFee) {
        this.combo = combo;
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.originalTuitionFee = BillingRules.money(originalTuitionFee);
    }
}
