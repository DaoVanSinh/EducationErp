package com.eduerp.modules.access;

import java.util.UUID;

/**
 * Cổng mà access tự khai báo để hỏi "account này còn tồn tại không" mà KHÔNG import
 * {@code com.eduerp.modules.identity.*}. Module identity cung cấp bean implement interface này
 * (Dependency Inversion) — chiều import vẫn chỉ là identity → access, không có cạnh ngược.
 *
 * <p>Kỹ thuật này mượn nguyên từ cách backend itsm (FastAPI) tách {@code rbac} khỏi {@code users}:
 * {@code rbac} không bao giờ {@code import app.modules.users}, mà khai một kiểu callback riêng
 * ({@code RbacTypes.UserLookup}) do {@code users} implement và tiêm vào lúc composition.
 */
public interface AccountExistenceCheck {

    boolean exists(UUID accountId);
}
