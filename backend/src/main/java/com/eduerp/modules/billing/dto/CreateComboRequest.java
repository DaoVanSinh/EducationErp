package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Tối thiểu 2 ghi danh ({@code MinimumComboSizeException} kiểm sau khi loại trùng). Hoá đơn của combo được lập tự động,
 * hạn là ngày thứ 7 tính từ khai giảng sớm nhất trong gói.
 */
public record CreateComboRequest(@NotNull UUID studentProfileId, @NotEmpty List<@NotNull UUID> enrollmentIds) {
}
