package com.eduerp.identity;

import java.io.Serializable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
class ScopedPermissionEvaluator implements PermissionEvaluator {

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
        String action = String.valueOf(permission);
        String requiredPrefix = IdentityConstants.Authorities.PERMISSION_AUTHORITY_PREFIX + targetType + ":" + action
                + ":";
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().startsWith(requiredPrefix));
    }
}
