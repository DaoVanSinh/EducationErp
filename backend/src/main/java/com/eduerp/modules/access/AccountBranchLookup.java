package com.eduerp.modules.access;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng để access biết chi nhánh chủ quản của một tài khoản (phục vụ {@link DataScope}) mà KHÔNG import
 * {@code identity} - identity cung cấp bean implement, cùng kỹ thuật với {@link AccountExistenceCheck}.
 */
public interface AccountBranchLookup {

    Optional<UUID> homeBranchOf(UUID accountId);
}
