package com.eduerp.modules.access.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Cùng hình dạng với {@link CreatePermissionGroupRequest} - sửa là gửi lại toàn bộ danh sách quyền. */
public record UpdatePermissionGroupRequest(
        @NotBlank String name,
        String description,
        @NotEmpty @Valid List<CreatePermissionGroupRequest.Item> items) {
}
