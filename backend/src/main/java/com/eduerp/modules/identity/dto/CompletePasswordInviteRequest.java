package com.eduerp.modules.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordInviteRequest(@NotBlank @Email String email, @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword) {
}
