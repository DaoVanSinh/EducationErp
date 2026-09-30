package com.eduerp.modules.organization;

import java.util.UUID;

/**
 * Sự kiện nghiệp vụ mà module organization phát ra, để {@code audit} tự lắng nghe qua
 * {@code @ApplicationModuleListener} (rule #17) thay vì bị gọi trực tiếp.
 */
public final class OrganizationEvents {

    private OrganizationEvents() {
    }

    public record BranchCreated(UUID branchId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record BranchUpdated(UUID branchId, UUID actorAccountId, UUID actorBranchId) {
    }
}
