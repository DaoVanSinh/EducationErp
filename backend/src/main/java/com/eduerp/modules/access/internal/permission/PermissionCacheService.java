package com.eduerp.modules.access.internal.permission;

import com.eduerp.integrations.cache.CacheKeyBuilder;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.internal.model.AccountRoleAssignment;
import com.eduerp.modules.access.internal.repository.AccountGroupMembershipRepository;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quyền nằm trong Redis chứ không trong JWT: token giữ gọn, và khi phân quyền đổi thì chỉ cần
 * evict key này là lần gọi API kế tiếp đã thấy quyền mới (frontend invalidate TanStack Query).
 *
 * <p>Tự đủ dữ liệu: role và group của một account đọc từ bảng do chính access sở hữu
 * ({@code account_roles}, {@code account_groups}), không đụng tới bảng {@code accounts} — access
 * không cần biết identity tồn tại để tính quyền.
 */
@Service
public class PermissionCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final AccountRoleAssignmentRepository accountRoles;
    private final AccountGroupMembershipRepository accountGroups;
    private final EffectivePermissionCalculator calculator;
    private final ObjectMapper mapper;

    PermissionCacheService(StringRedisTemplate redis, AccountRoleAssignmentRepository accountRoles,
            AccountGroupMembershipRepository accountGroups, EffectivePermissionCalculator calculator,
            ObjectMapper mapper) {
        this.redis = redis;
        this.accountRoles = accountRoles;
        this.accountGroups = accountGroups;
        this.calculator = calculator;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Set<EffectivePermission> getEffectivePermissions(UUID accountId) {
        String key = CacheKeyBuilder.key(AccessConstants.CacheNamespaces.EFFECTIVE_PERMISSIONS, accountId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return deserialize(cached);
        }
        // Account chưa từng được gán role (vd vừa tạo, chưa kịp assignRole) thì coi như chưa có
        // quyền gì — không phải lỗi, vì access không có quyền khẳng định account đó tồn tại hay không.
        var role = accountRoles.findById(accountId).map(AccountRoleAssignment::getRole).orElse(null);
        if (role == null) {
            return Set.of();
        }
        var groups = new HashSet<>(accountGroups.findGroupsByAccountId(accountId));
        var computed = calculator.calculate(role, groups);
        redis.opsForValue().set(key, serialize(computed), TTL);
        return computed;
    }

    public void evict(UUID accountId) {
        redis.delete(CacheKeyBuilder.key(AccessConstants.CacheNamespaces.EFFECTIVE_PERMISSIONS, accountId));
    }

    private String serialize(Set<EffectivePermission> permissions) {
        try {
            return mapper.writeValueAsString(permissions);
        } catch (Exception e) {
            throw new IllegalStateException("Không serialize được permission set", e);
        }
    }

    private Set<EffectivePermission> deserialize(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(Set.class, EffectivePermission.class));
        } catch (Exception e) {
            throw new IllegalStateException("Không deserialize được permission set", e);
        }
    }
}
