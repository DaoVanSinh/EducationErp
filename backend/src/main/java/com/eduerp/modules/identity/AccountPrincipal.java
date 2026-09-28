package com.eduerp.modules.identity;

import java.util.UUID;

/** Danh tính của người gọi sau khi filter xác thực xong — gắn vào {@code Authentication}. */
public record AccountPrincipal(UUID accountId, UUID homeBranchId) {
}
