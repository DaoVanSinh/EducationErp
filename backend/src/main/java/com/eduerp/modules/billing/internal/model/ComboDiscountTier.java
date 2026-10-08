package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Cấu hình admin ở mức module, không thuộc một {@code Combo} cụ thể: "từ N khoá trở lên giảm X%".
 * Không có thao tác xoá - hệ thống này không dùng {@code Actions.DELETE} ở bất kỳ resource nào, nên
 * "xoá" một bậc nghĩa là tắt {@code active} (mirror {@code Course.active}).
 */
@Entity
@Table(name = "combo_discount_tiers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComboDiscountTier {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "min_course_count", nullable = false, unique = true, updatable = false)
    private int minCourseCount;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent;

    @Column(nullable = false)
    private boolean active = true;

    public ComboDiscountTier(int minCourseCount, BigDecimal discountPercent) {
        this.minCourseCount = minCourseCount;
        this.discountPercent = BillingRules.percent(discountPercent);
        this.active = true;
    }

    /** {@code minCourseCount} không sửa được: đổi mốc của một bậc đã dùng là tạo một bậc khác. */
    public void update(BigDecimal discountPercent, boolean active) {
        this.discountPercent = BillingRules.percent(discountPercent);
        this.active = active;
    }
}
