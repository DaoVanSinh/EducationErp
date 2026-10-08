package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Không phân trang: số bậc giảm giá là một con số nhỏ do admin tự nhập (2 khoá, 3 khoá, 5 khoá...),
 * một trang là đủ và màn hình cấu hình cần thấy hết cùng lúc để so sánh các mốc.
 *
 * <p>Trả CẢ bậc đã tắt {@code active}: đó là cách duy nhất để admin bật lại một bậc đã "xoá".
 */
@Service
public class ListComboDiscountTiers {

    private final ComboDiscountTierRepository tiers;

    ListComboDiscountTiers(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<ComboDiscountTierResponse> execute() {
        return tiers.findAllByOrderByMinCourseCountAsc().stream()
                .map(ListComboDiscountTiers::toResponse).toList();
    }

    /** Dùng lại ở {@code CreateComboDiscountTier}/{@code UpdateComboDiscountTier} - một chỗ map duy nhất. */
    static ComboDiscountTierResponse toResponse(ComboDiscountTier tier) {
        return new ComboDiscountTierResponse(tier.getId(), tier.getMinCourseCount(),
                tier.getDiscountPercent(), tier.isActive());
    }
}
