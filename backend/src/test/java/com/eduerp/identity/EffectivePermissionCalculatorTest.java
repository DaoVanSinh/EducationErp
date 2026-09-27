package com.eduerp.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectivePermissionCalculatorTest {

    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();

    @Test
    void takesBroadestScopeWhenSamePermissionGrantedTwice() {
        var permission = new Permission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.UPDATE);

        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(permission, IdentityConstants.PermissionScope.PERSONAL);
        var role = new Role(IdentityConstants.RoleCodes.TEACHER, "Giáo viên", true);
        role.addPermissionGroup(roleGroup);

        var extraGroup = new PermissionGroup("Từ group", null);
        extraGroup.addItem(permission, IdentityConstants.PermissionScope.BRANCH);
        var group = new Group("Phòng đào tạo", null);
        group.addPermissionGroup(extraGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactly(
                new EffectivePermission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.UPDATE,
                        IdentityConstants.PermissionScope.BRANCH));
    }

    @Test
    void unionsDistinctPermissionsFromRoleAndGroups() {
        var readPermission = new Permission(IdentityConstants.Resources.BRANCH, IdentityConstants.Actions.READ);
        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(readPermission, IdentityConstants.PermissionScope.ORGANIZATION);
        var role = new Role(IdentityConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(roleGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of());

        assertThat(result).hasSize(1);
    }
}
