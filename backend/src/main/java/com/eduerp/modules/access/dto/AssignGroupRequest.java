package com.eduerp.modules.access.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignGroupRequest(@NotNull UUID groupId) {
}
