package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.billing.usecase.CreateComboDiscountTier;
import com.eduerp.modules.billing.usecase.ListComboDiscountTiers;
import com.eduerp.modules.billing.usecase.UpdateComboDiscountTier;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cấu hình bậc giảm giá combo - mirror {@code CourseAdminController} về cấu trúc (PATCH để sửa,
 * không có DELETE: tắt {@code active} là cách "xoá" một bậc).
 *
 * <p>Dùng lại quyền {@code INVOICE} (spec mục 8): đây là cấu hình giá, cùng nhóm quyền với người
 * quản lý học phí - không cần một resource RBAC tách riêng cho một bảng cấu hình nhỏ.
 *
 * <p>Không phân trang và không nhận {@code Pageable}: số bậc là một con số nhỏ do admin tự nhập, màn
 * hình cấu hình cần thấy hết cùng lúc để so sánh các mốc.
 */
@RestController
@RequestMapping("/api/billing/combo-discount-tiers")
class ComboDiscountTierAdminController {

    private final ListComboDiscountTiers listComboDiscountTiers;
    private final CreateComboDiscountTier createComboDiscountTier;
    private final UpdateComboDiscountTier updateComboDiscountTier;

    ComboDiscountTierAdminController(ListComboDiscountTiers listComboDiscountTiers,
            CreateComboDiscountTier createComboDiscountTier,
            UpdateComboDiscountTier updateComboDiscountTier) {
        this.listComboDiscountTiers = listComboDiscountTiers;
        this.createComboDiscountTier = createComboDiscountTier;
        this.updateComboDiscountTier = updateComboDiscountTier;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    List<ComboDiscountTierResponse> list() {
        return listComboDiscountTiers.execute();
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    ComboDiscountTierResponse create(@Valid @RequestBody CreateComboDiscountTierRequest request) {
        return createComboDiscountTier.execute(request);
    }

    @PatchMapping("/{tierId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    ComboDiscountTierResponse update(@PathVariable UUID tierId,
            @Valid @RequestBody UpdateComboDiscountTierRequest request) {
        return updateComboDiscountTier.execute(tierId, request);
    }
}
