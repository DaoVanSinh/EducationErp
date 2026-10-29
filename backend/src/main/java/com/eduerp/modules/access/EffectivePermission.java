package com.eduerp.modules.access;

import java.util.Collection;

/** Một quyền CRUD trên resource kèm cấp độ (cá nhân / chi nhánh / tổ chức). */
public record EffectivePermission(String resource, String action, AccessConstants.PermissionScope scope) {

    /** Khoá hợp nhất: cùng resource+action thì chỉ giữ bản có scope rộng nhất. */
    public String key() {
        return resource + ":" + action;
    }

    /**
     * Có quyền {@code resource:action} ở BẤT KỲ scope nào không - câu hỏi "tài khoản này có năng lực X
     * không" (vd giáo viên = có cổng giảng dạy), thay cho so mã vai trò (rbac.md: permissions over roles).
     */
    public static boolean grants(Collection<EffectivePermission> permissions, String resource, String action) {
        return permissions.stream()
                .anyMatch(permission -> permission.resource().equals(resource) && permission.action().equals(action));
    }
}
