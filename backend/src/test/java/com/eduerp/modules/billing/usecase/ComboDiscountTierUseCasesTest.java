package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.ComboDiscountTierAlreadyExistsException;
import com.eduerp.modules.billing.ComboDiscountTierNotFoundException;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ComboDiscountTierUseCasesTest {

    private final ComboDiscountTierRepository tiers = mock(ComboDiscountTierRepository.class);
    private final ListComboDiscountTiers listTiers = new ListComboDiscountTiers(tiers);
    private final CreateComboDiscountTier createTier = new CreateComboDiscountTier(tiers);
    private final UpdateComboDiscountTier updateTier = new UpdateComboDiscountTier(tiers);

    /** Màn hình cấu hình liệt kê CẢ bậc đã tắt, để admin bật lại được - không lọc active ở đây. */
    @Test
    void listReturnsEveryTierIncludingInactiveOnesInRepositoryOrder() {
        var twoCourses = new ComboDiscountTier(2, new BigDecimal("10"));
        var fiveCourses = new ComboDiscountTier(5, new BigDecimal("25"));
        fiveCourses.update(new BigDecimal("25"), false);
        when(tiers.findAllByOrderByMinCourseCountAsc()).thenReturn(List.of(twoCourses, fiveCourses));

        var response = listTiers.execute();

        assertThat(response).extracting(ComboDiscountTierResponse::minCourseCount).containsExactly(2, 5);
        assertThat(response).extracting(ComboDiscountTierResponse::active).containsExactly(true, false);
        assertThat(response.get(0).discountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void createSavesANewTierAsActive() {
        when(tiers.saveAndFlush(any(ComboDiscountTier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = createTier.execute(new CreateComboDiscountTierRequest(3, new BigDecimal("15")));

        assertThat(response.minCourseCount()).isEqualTo(3);
        assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(response.active()).isTrue();
    }

    /** min_course_count UNIQUE (V21): hai bậc cùng mốc khiến việc chọn bậc phụ thuộc thứ tự dòng -
     * phải là 409 có errorCode, không phải 500. */
    @Test
    void createTranslatesAUniqueViolationIntoComboDiscountTierAlreadyExists() {
        when(tiers.saveAndFlush(any(ComboDiscountTier.class)))
                .thenThrow(new DataIntegrityViolationException("combo_discount_tiers_min_course_count_key"));

        assertThatThrownBy(() -> createTier.execute(
                new CreateComboDiscountTierRequest(3, new BigDecimal("15"))))
                .isInstanceOf(ComboDiscountTierAlreadyExistsException.class)
                .hasMessageContaining("3");
    }

    @Test
    void updateChangesThePercentAndTheActiveFlagButNotTheThreshold() {
        var tier = new ComboDiscountTier(3, new BigDecimal("15"));
        var tierId = UUID.randomUUID();
        when(tiers.findById(tierId)).thenReturn(Optional.of(tier));

        var response = updateTier.execute(tierId,
                new UpdateComboDiscountTierRequest(new BigDecimal("18.5"), false));

        assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("18.50"));
        assertThat(response.active()).isFalse();
        assertThat(response.minCourseCount()).isEqualTo(3);
    }

    @Test
    void updateRejectsAnUnknownTier() {
        var tierId = UUID.randomUUID();
        when(tiers.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateTier.execute(tierId,
                new UpdateComboDiscountTierRequest(new BigDecimal("18.5"), true)))
                .isInstanceOf(ComboDiscountTierNotFoundException.class);
    }
}
