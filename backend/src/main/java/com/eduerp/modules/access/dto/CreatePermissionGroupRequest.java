package com.eduerp.modules.access.dto;

import com.eduerp.modules.access.AccessConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreatePermissionGroupRequest(
        @NotBlank String name,
        String description,
        @NotEmpty @Valid List<Item> items) {

    /**
     * scope là enum chứ không phải String: giá trị sai bị chặn ngay ở tầng deserialize thành 400,
     * thay vì đi tới {@code valueOf} trong use case và trồi lên thành 500.
     */
    public record Item(@NotNull UUID permissionId, @NotNull AccessConstants.PermissionScope scope) {
    }
}
