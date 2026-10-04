package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.ComboDiscountTierAlreadyExistsException;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mốc trùng được chặn bởi UNIQUE {@code min_course_count} (V21) thay vì một lần đọc trước khi ghi -
 * đọc-rồi-ghi vẫn để hở race giữa hai admin, còn UNIQUE thì không. */
@Service
public class CreateComboDiscountTier {

    private final ComboDiscountTierRepository tiers;

    CreateComboDiscountTier(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional
    public ComboDiscountTierResponse execute(CreateComboDiscountTierRequest request) {
        try {
            return ListComboDiscountTiers.toResponse(tiers.saveAndFlush(
                    new ComboDiscountTier(request.minCourseCount(), request.discountPercent())));
        } catch (DataIntegrityViolationException duplicateThreshold) {
            throw new ComboDiscountTierAlreadyExistsException(request.minCourseCount());
        }
    }
}
