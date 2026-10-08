package com.eduerp.modules.academic.internal.rules;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import java.util.Set;

/** Pure, không I/O - phần quyết định của bước 3 trong spec mục 6, tách khỏi phần gọi facade. */
public final class GradeAccessRules {

    private GradeAccessRules() {
    }

    /**
     * Caller có quyền {@code GRADE:<action>} ở scope {@code ORGANIZATION} hay không. Chỉ scope này
     * được bỏ qua bước so {@code teacherId} (spec mục 6 bước 3); {@code PERSONAL} và {@code BRANCH}
     * đều phải đi tiếp (spec mục 6 bước 4).
     */
    public static boolean allowsEveryClass(Set<EffectivePermission> permissions, String action) {
        return permissions.stream().anyMatch(permission ->
                AccessConstants.Resources.GRADE.equals(permission.resource())
                        && action.equals(permission.action())
                        && permission.scope() == AccessConstants.PermissionScope.ORGANIZATION);
    }
}
