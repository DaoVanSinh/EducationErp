package com.eduerp.modules.academic.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure, không I/O: tách phần "scope này có phải toàn quyền tổ chức không" khỏi phần gọi facade để
 * test được không cần mock. */
class GradeAccessRulesTest {

    private static EffectivePermission grade(String action, AccessConstants.PermissionScope scope) {
        return new EffectivePermission(AccessConstants.Resources.GRADE, action, scope);
    }

    /** Spec mục 6 bước 3: scope ORGANIZATION → bỏ qua hoàn toàn bước kiểm phân công (Review Focus #5). */
    @Test
    void anOrganizationScopedGradePermissionAllowsEveryClass() {
        var permissions = Set.of(grade(AccessConstants.Actions.UPDATE,
                AccessConstants.PermissionScope.ORGANIZATION));

        assertThat(GradeAccessRules.allowsEveryClass(permissions, AccessConstants.Actions.UPDATE)).isTrue();
    }

    /** Spec mục 6 bước 4: PERSONAL và BRANCH đều phải đi tiếp qua bước so teacherId. */
    @Test
    void personalAndBranchScopesDoNotAllowEveryClass() {
        assertThat(GradeAccessRules.allowsEveryClass(
                Set.of(grade(AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.PERSONAL)),
                AccessConstants.Actions.UPDATE)).isFalse();
        assertThat(GradeAccessRules.allowsEveryClass(
                Set.of(grade(AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.BRANCH)),
                AccessConstants.Actions.UPDATE)).isFalse();
    }

    /** Quyền ở resource/action KHÁC không được dùng để mở cửa: READ:ORGANIZATION không cho phép
     * UPDATE trên mọi lớp. */
    @Test
    void doesNotMixUpActions() {
        var readOnlyAdmin = Set.of(grade(AccessConstants.Actions.READ,
                AccessConstants.PermissionScope.ORGANIZATION));

        assertThat(GradeAccessRules.allowsEveryClass(readOnlyAdmin, AccessConstants.Actions.READ)).isTrue();
        assertThat(GradeAccessRules.allowsEveryClass(readOnlyAdmin, AccessConstants.Actions.UPDATE)).isFalse();
    }

    @Test
    void doesNotMixUpResources() {
        var otherResource = Set.of(new EffectivePermission(AccessConstants.Resources.ENROLLMENT,
                AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.ORGANIZATION));

        assertThat(GradeAccessRules.allowsEveryClass(otherResource, AccessConstants.Actions.UPDATE)).isFalse();
    }

    @Test
    void anEmptyPermissionSetAllowsNothing() {
        assertThat(GradeAccessRules.allowsEveryClass(Set.of(), AccessConstants.Actions.UPDATE)).isFalse();
    }
}
