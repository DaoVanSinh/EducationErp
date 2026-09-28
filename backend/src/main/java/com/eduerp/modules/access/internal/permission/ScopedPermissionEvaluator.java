package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.internal.rules.AccessRules;
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
     * {@code AccessConstants.AccessRules}. Không có phần này thì mặc định là PERSONAL — tức mọi
     * scope đều qua, đúng cho những endpoint chỉ tác động lên chính người gọi.
     */
    private static final char MINIMUM_SCOPE_MARKER = '@';

    private final AccessRules rules;

    public ScopedPermissionEvaluator(AccessRules rules) {
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
                ? AccessConstants.PermissionScope.PERSONAL
                : AccessConstants.PermissionScope.valueOf(requested.substring(markerAt + 1));
        String requiredPrefix = AccessConstants.Authorities.PERMISSION_AUTHORITY_PREFIX + targetType + ":" + action
                + ":";
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(granted -> granted.startsWith(requiredPrefix))
                .map(granted -> AccessConstants.PermissionScope.valueOf(granted.substring(requiredPrefix.length())))
                .anyMatch(granted -> rules.isBroaderOrEqual(granted, minimumScope));
    }
}
