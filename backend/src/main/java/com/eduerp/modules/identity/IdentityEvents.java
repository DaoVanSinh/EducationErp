package com.eduerp.modules.identity;

import java.util.UUID;

/**
 * Sự kiện nghiệp vụ mà module identity phát ra, cho {@code audit} lắng nghe qua
 * {@code @ApplicationModuleListener} (rule #17). identity không hề biết audit tồn tại.
 */
public final class IdentityEvents {

    private IdentityEvents() {
    }

    /**
     * Một tài khoản vừa đăng nhập thành công. Trước đây use case {@code Login} phải ghi audit trực
     * tiếp vì lúc đó {@code SecurityContext} còn rỗng — với sự kiện, actor được truyền tường minh
     * ngay trong payload nên không còn trường hợp đặc biệt đó nữa.
     */
    public record AccountSignedIn(UUID accountId, UUID branchId) {
    }

    public record AccountBranchTransferred(UUID accountId, UUID newBranchId, UUID actorAccountId,
            UUID actorBranchId) {
    }
}
