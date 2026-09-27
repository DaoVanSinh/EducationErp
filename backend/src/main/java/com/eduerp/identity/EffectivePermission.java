package com.eduerp.identity;

public record EffectivePermission(String resource, String action, IdentityConstants.PermissionScope scope) {

    String key() {
        return resource + ":" + action;
    }
}
