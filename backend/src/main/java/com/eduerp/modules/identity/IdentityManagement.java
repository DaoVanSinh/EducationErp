package com.eduerp.modules.identity;

import com.eduerp.modules.identity.internal.permission.PermissionCacheService;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Facade của module identity — type DUY NHẤT mà module khác được phép gọi (rule #1).
 * Mọi thứ còn lại nằm trong {@code usecase}, {@code web} hoặc {@code internal}.
 */
@Service
public class IdentityManagement {

    private final PermissionCacheService permissions;

    IdentityManagement(PermissionCacheService permissions) {
        this.permissions = permissions;
    }

    public Set<EffectivePermission> effectivePermissions(UUID accountId) {
        return permissions.getEffectivePermissions(accountId);
    }

    /** Gọi sau khi role/group của tài khoản đổi để UI nhận quyền mới ngay lần gọi API kế tiếp. */
    public void evictPermissionCache(UUID accountId) {
        permissions.evict(accountId);
    }
}
