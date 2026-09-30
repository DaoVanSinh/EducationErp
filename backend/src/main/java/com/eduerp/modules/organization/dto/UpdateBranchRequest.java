package com.eduerp.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;

/** Không có {@code code}: mã chi nhánh bất biến sau khi tạo, entity {@code Branch} không có setter cho nó. */
public record UpdateBranchRequest(@NotBlank String name, String address, boolean active) {
}
