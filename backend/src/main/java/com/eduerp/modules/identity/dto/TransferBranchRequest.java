package com.eduerp.modules.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferBranchRequest(@NotNull UUID branchId) {
}
