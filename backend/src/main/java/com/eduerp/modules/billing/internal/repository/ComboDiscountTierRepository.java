package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboDiscountTierRepository extends JpaRepository<ComboDiscountTier, UUID> {

    /** Màn hình cấu hình liệt kê theo mốc tăng dần - gồm cả bậc đã tắt, để admin bật lại được. */
    List<ComboDiscountTier> findAllByOrderByMinCourseCountAsc();

    /**
     * Spec mục 4: bậc áp dụng = {@code minCourseCount} lớn nhất còn {@code active} mà không vượt số
     * khoá trong combo. Để DB chọn thay vì lọc/sắp trong Java - một câu query, không N+1, và quy tắc
     * chọn nằm đúng một chỗ.
     */
    Optional<ComboDiscountTier> findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(
            int courseCount);
}
