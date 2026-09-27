package com.eduerp.modules.identity.internal.permission;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.rules.IdentityRules;
import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Cầu nối {@code hasPermission(resource, action)} trong {@code @PreAuthorize} tới authority đã nạp từ cache. */
@Component
public class ScopedPermissionEvaluator implements PermissionEvaluator {

    /**
     * Ngăn giữa action và scope tối thiểu trong biểu thức kiểm tra, khớp với
     * {@code IdentityConstants.AccessRules}. Không có phần này thì mặc định là PERSONAL —
     * tức mọi scope đều qua, đúng cho những endpoint chỉ tác động lên chính người gọi.
     */
    private static final char MINIMUM_SCOPE_MARKER = '@';

    private final IdentityRules rules;

    public ScopedPermissionEvaluator(IdentityRules rules) {
        this.rules = rules;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
            Object permission) {
        if (authentication == null) {
            return false;
        }
        String requested = String.valueOf(permission);
        int markerAt = requested.indexOf(MINIMUM_SCOPE_MARKER);
        String action = markerAt < 0 ? requested : requested.substring(0, markerAt);
        var minimumScope = markerAt < 0
                ? IdentityConstants.PermissionScope.PERSONAL
                : IdentityConstants.PermissionScope.valueOf(requested.substring(markerAt + 1));
        String requiredPrefix = IdentityConstants.Authorities.PERMISSION_AUTHORITY_PREFIX + targetType + ":" + action
                + ":";
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(granted -> granted.startsWith(requiredPrefix))
                .map(granted -> IdentityConstants.PermissionScope.valueOf(granted.substring(requiredPrefix.length())))
                .anyMatch(granted -> rules.isBroaderOrEqual(granted, minimumScope));
    }
}
