package com.eduerp.modules.access;

/** Một quyền CRUD trên resource kèm cấp độ (cá nhân / chi nhánh / tổ chức). */
public record EffectivePermission(String resource, String action, AccessConstants.PermissionScope scope) {

    /** Khoá hợp nhất: cùng resource+action thì chỉ giữ bản có scope rộng nhất. */
    public String key() {
        return resource + ":" + action;
    }
}
