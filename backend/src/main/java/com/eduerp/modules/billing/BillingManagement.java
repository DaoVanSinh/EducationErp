package com.eduerp.modules.billing;

import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module billing — cung cấp thông tin công khai (như các bậc giảm giá combo đang áp
 * dụng) cho các module khác mà không lộ entity {@code ComboDiscountTier} (rule #1).
 */
@Service
public class BillingManagement {

    public record ActiveComboTierSnapshot(UUID id, int minCourseCount, BigDecimal discountPercent) {
    }

    private final ComboDiscountTierRepository tiers;

    BillingManagement(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<ActiveComboTierSnapshot> listActiveComboDiscountTiers() {
        return tiers.findAllByActiveTrueOrderByMinCourseCountAsc().stream()
                .map(tier -> new ActiveComboTierSnapshot(tier.getId(), tier.getMinCourseCount(),
                        tier.getDiscountPercent()))
                .toList();
    }
}
