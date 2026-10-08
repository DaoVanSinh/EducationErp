package com.eduerp.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBranchRequest(@NotBlank String code, @NotBlank String name, String address) {
}
