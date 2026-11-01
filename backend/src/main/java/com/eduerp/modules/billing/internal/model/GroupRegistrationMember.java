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
 * Một người trong nhóm. {@code group} là quan hệ JPA thật (cùng module); {@code enrollmentId} và
 * {@code studentProfileId} là UUID trần vì thuộc module khác - mirror {@link ComboEnrollment}.
 */
@Entity
@Table(name = "group_registration_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupRegistrationMember {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private GroupRegistration group;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    /** Snapshot học phí khoá lúc lập nhóm. */
    @Column(name = "original_tuition_fee", nullable = false, updatable = false)
    private BigDecimal originalTuitionFee;

    /** Trần mới cho tổng hoá đơn của ghi danh này. */
    @Column(name = "discounted_tuition_fee", nullable = false, updatable = false)
    private BigDecimal discountedTuitionFee;

    GroupRegistrationMember(GroupRegistration group, UUID enrollmentId, UUID studentProfileId,
            BigDecimal originalTuitionFee, BigDecimal discountedTuitionFee) {
        this.group = group;
        this.enrollmentId = enrollmentId;
        this.studentProfileId = studentProfileId;
        this.originalTuitionFee = BillingRules.money(originalTuitionFee);
        this.discountedTuitionFee = BillingRules.money(discountedTuitionFee);
    }
}
