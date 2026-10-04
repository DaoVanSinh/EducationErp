package com.eduerp.modules.billing.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class ComboRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    ComboDiscountTierRepository tiers;

    @Test
    void savesATierAsActiveWithPercentAtScaleTwo() {
        var saved = tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));

        var found = tiers.findById(saved.getId()).orElseThrow();
        assertThat(found.getMinCourseCount()).isEqualTo(2);
        assertThat(found.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(found.isActive()).isTrue();
    }

    /** min_course_count UNIQUE: hai bậc cùng mốc sẽ khiến việc chọn bậc phụ thuộc thứ tự dòng. */
    @Test
    void rejectsASecondTierWithTheSameMinCourseCountAtDatabaseLevel() {
        tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));

        assertThatThrownBy(() -> tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("20"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Spec mục 4: bậc áp dụng là minCourseCount LỚN NHẤT còn active mà không vượt số khoá. */
    @Test
    void picksTheHighestActiveTierThatDoesNotExceedTheCourseCount() {
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));
        tiers.saveAndFlush(new ComboDiscountTier(5, new BigDecimal("25")));

        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(4)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(1))
                .isEmpty();
    }

    /** Tắt active là cách duy nhất để "xoá" một bậc - bậc tắt không được chọn nữa. */
    @Test
    void skipsAnInactiveTierAndFallsBackToTheNextOneDown() {
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        var retired = tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));
        retired.update(new BigDecimal("15"), false);
        tiers.saveAndFlush(retired);

        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(3)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void listsTiersInAscendingMinCourseCountOrder() {
        tiers.saveAndFlush(new ComboDiscountTier(5, new BigDecimal("25")));
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));

        assertThat(tiers.findAllByOrderByMinCourseCountAsc())
                .extracting(ComboDiscountTier::getMinCourseCount).containsExactly(2, 5);
    }
}
