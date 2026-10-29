package com.eduerp.modules.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Không có {@code code}: mã vai trò bất biến sau khi tạo. */
public record UpdateRoleRequest(@NotBlank String name, @NotNull List<UUID> permissionGroupIds) {
}
