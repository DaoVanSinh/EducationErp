package com.eduerp.modules.billing.internal.pricing;

import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Một chỗ trả lời "ghi danh này đang hưởng ưu đãi nào": nằm trong combo (thu qua hoá đơn combo) hay trong
 * một nhóm (trần học phí đã giảm). Hai ưu đãi loại trừ nhau - một ghi danh không được giảm hai lần.
 */
@Component
public class EnrollmentDiscounts {

    private final ComboRepository combos;
    private final GroupRegistrationRepository groups;

    EnrollmentDiscounts(ComboRepository combos, GroupRegistrationRepository groups) {
        this.combos = combos;
        this.groups = groups;
    }

    public boolean isInCombo(UUID enrollmentId) {
        return combos.existsEnrollment(enrollmentId);
    }

    /** Học phí đã giảm nếu ghi danh thuộc một nhóm; rỗng nghĩa là tính theo học phí niêm yết của khoá. */
    public Optional<BigDecimal> groupTuitionOf(UUID enrollmentId) {
        return groups.findMemberByEnrollmentId(enrollmentId).map(member -> member.getDiscountedTuitionFee());
    }

    public boolean isDiscounted(UUID enrollmentId) {
        return isInCombo(enrollmentId) || groupTuitionOf(enrollmentId).isPresent();
    }
}
