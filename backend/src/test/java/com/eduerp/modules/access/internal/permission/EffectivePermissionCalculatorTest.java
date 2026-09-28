package com.eduerp.modules.access.internal.permission;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.internal.model.Group;
import com.eduerp.modules.access.internal.model.Permission;
import com.eduerp.modules.access.internal.model.PermissionGroup;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.rules.AccessRules;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectivePermissionCalculatorTest {

    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator(new AccessRules());

    @Test
    void takesBroadestScopeWhenSamePermissionGrantedTwice() {
        var permission = new Permission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.UPDATE);

        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(permission, AccessConstants.PermissionScope.PERSONAL);
        var role = new Role(AccessConstants.RoleCodes.TEACHER, "Giáo viên", true);
        role.addPermissionGroup(roleGroup);

        var extraGroup = new PermissionGroup("Từ group", null);
        extraGroup.addItem(permission, AccessConstants.PermissionScope.BRANCH);
        var group = new Group("Phòng đào tạo", null);
        group.addPermissionGroup(extraGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactly(
                new EffectivePermission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.UPDATE,
                        AccessConstants.PermissionScope.BRANCH));
    }

    @Test
    void keepsBroaderScopeWhenRoleGrantsWiderScopeThanGroup() {
        var permission = new Permission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.DELETE);

        var roleGroup = new PermissionGroup("Từ role - rộng", null);
        roleGroup.addItem(permission, AccessConstants.PermissionScope.ORGANIZATION);
        var role = new Role(AccessConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(roleGroup);

        var narrowGroup = new PermissionGroup("Từ group - hẹp", null);
        narrowGroup.addItem(permission, AccessConstants.PermissionScope.PERSONAL);
        var group = new Group("Phòng ban", null);
        group.addPermissionGroup(narrowGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactly(
                new EffectivePermission(AccessConstants.Resources.ACCOUNT, AccessConstants.Actions.DELETE,
                        AccessConstants.PermissionScope.ORGANIZATION));
    }

    @Test
    void unionsDistinctPermissionsFromRoleAndGroups() {
        var rolePermission = new Permission(AccessConstants.Resources.BRANCH, AccessConstants.Actions.READ);
        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(rolePermission, AccessConstants.PermissionScope.ORGANIZATION);
        var role = new Role(AccessConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(roleGroup);

        var groupPermission = new Permission(AccessConstants.Resources.GROUP, AccessConstants.Actions.READ);
        var extraGroup = new PermissionGroup("Từ group", null);
        extraGroup.addItem(groupPermission, AccessConstants.PermissionScope.BRANCH);
        var group = new Group("Phòng ban", null);
        group.addPermissionGroup(extraGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactlyInAnyOrder(
                new EffectivePermission(AccessConstants.Resources.BRANCH, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.ORGANIZATION),
                new EffectivePermission(AccessConstants.Resources.GROUP, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.BRANCH));
    }
}
