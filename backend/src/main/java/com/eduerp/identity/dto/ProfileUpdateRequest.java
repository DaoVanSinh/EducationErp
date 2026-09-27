package com.eduerp.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileUpdateRequest(@NotBlank String fullName, String avatarUrl) {
}
