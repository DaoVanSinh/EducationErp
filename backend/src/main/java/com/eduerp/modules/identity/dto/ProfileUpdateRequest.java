package com.eduerp.modules.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(@NotBlank String fullName, String avatarUrl) {
}
