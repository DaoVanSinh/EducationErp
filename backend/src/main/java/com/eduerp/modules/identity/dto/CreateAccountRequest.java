package com.eduerp.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAccountRequest(@NotBlank @Email String email, @NotBlank String fullName, UUID homeBranchId,
        @NotNull UUID roleId) {
}
