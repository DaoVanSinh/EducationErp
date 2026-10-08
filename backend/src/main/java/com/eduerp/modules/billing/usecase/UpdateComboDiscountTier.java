package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.ComboDiscountTierNotFoundException;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sửa % giảm và bật/tắt một bậc. Đổi {@code minCourseCount} KHÔNG được hỗ trợ: đó là danh tính của
 * bậc (cột UNIQUE), đổi nó là tạo một bậc khác.
 *
 * <p>Combo đã tạo không bị ảnh hưởng: {@code Combo.discountPercent} là snapshot lúc tạo (spec mục 4).
 */
@Service
public class UpdateComboDiscountTier {

    private final ComboDiscountTierRepository tiers;

    UpdateComboDiscountTier(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional
    public ComboDiscountTierResponse execute(UUID tierId, UpdateComboDiscountTierRequest request) {
        var tier = tiers.findById(tierId).orElseThrow(() -> new ComboDiscountTierNotFoundException(tierId));
        tier.update(request.discountPercent(), request.active());
        return ListComboDiscountTiers.toResponse(tier);
    }
}
