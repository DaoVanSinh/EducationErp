package com.eduerp.modules.access;

import java.util.UUID;

/**
 * Sự kiện nghiệp vụ mà module access phát ra. Thay cho AOP {@code @Audited} trước đây: khi các use
 * case audited nằm rải ở nhiều module khác nhau, một aspect xuyên module để đọc trực tiếp
 * repository của {@code audit} là sai ranh giới (rule #1) — publish sự kiện rồi để {@code audit}
 * tự lắng nghe qua {@code @ApplicationModuleListener} mới là cách rule #17 quy định.
 *
 * <p>{@code actorAccountId}/{@code actorBranchId} luôn được use case truyền tường minh (lấy từ
 * {@code AccountPrincipal} ở tầng controller), không đọc ngầm từ {@code SecurityContext} nữa — ai
 * làm việc gì là dữ liệu nghiệp vụ, nên đi qua tham số như mọi dữ liệu khác.
 */
public final class AccessEvents {

    private AccessEvents() {
    }

    public record RoleCreated(UUID roleId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PermissionGroupCreated(UUID permissionGroupId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record AccountJoinedGroup(UUID accountId, UUID groupId, UUID actorAccountId, UUID actorBranchId) {
    }
}
