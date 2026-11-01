package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.internal.model.Permission;
import com.eduerp.modules.access.internal.model.PermissionGroup;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.rules.AccessRules;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Quyền hiệu lực chỉ tính từ vai trò; một vai trò có thể gồm nhiều nhóm quyền chồng lên nhau. */
class EffectivePermissionCalculatorTest {

    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator(new AccessRules());

    @Test
    void takesBroadestScopeWhenTwoPermissionGroupsGrantTheSamePermission() {
        var permission = new Permission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.UPDATE);
        var narrow = new PermissionGroup("Hẹp", null);
        narrow.addItem(permission, AccessConstants.PermissionScope.PERSONAL);
        var wide = new PermissionGroup("Rộng", null);
        wide.addItem(permission, AccessConstants.PermissionScope.BRANCH);
        var role = new Role(AccessConstants.RoleCodes.TEACHER, "Giáo viên", true);
        role.addPermissionGroup(narrow);
        role.addPermissionGroup(wide);

        Set<EffectivePermission> result = calculator.calculate(role);

        assertThat(result).containsExactly(
                new EffectivePermission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.UPDATE,
                        AccessConstants.PermissionScope.BRANCH));
    }

    @Test
    void unionsDistinctPermissionsFromAllPermissionGroupsOfTheRole() {
        var branchRead = new Permission(AccessConstants.Resources.BRANCH, AccessConstants.Actions.READ);
        var first = new PermissionGroup("Chi nhánh", null);
        first.addItem(branchRead, AccessConstants.PermissionScope.ORGANIZATION);
        var roleRead = new Permission(AccessConstants.Resources.ROLE, AccessConstants.Actions.READ);
        var second = new PermissionGroup("Vai trò", null);
        second.addItem(roleRead, AccessConstants.PermissionScope.BRANCH);
        var role = new Role(AccessConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(first);
        role.addPermissionGroup(second);

        Set<EffectivePermission> result = calculator.calculate(role);

        assertThat(result).containsExactlyInAnyOrder(
                new EffectivePermission(AccessConstants.Resources.BRANCH, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.ORGANIZATION),
                new EffectivePermission(AccessConstants.Resources.ROLE, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.BRANCH));
    }

    @Test
    void aRoleWithoutPermissionGroupsGrantsNothing() {
        var role = new Role(AccessConstants.RoleCodes.STUDENT, "Học viên", true);

        assertThat(calculator.calculate(role)).isEmpty();
    }
}
