package com.eduerp.modules.organization.dto;

import java.util.UUID;

public record BranchResponse(UUID id, String code, String name, String address, boolean active) {
}
