package com.eduerp.modules.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateRoleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull List<UUID> permissionGroupIds) {
}
