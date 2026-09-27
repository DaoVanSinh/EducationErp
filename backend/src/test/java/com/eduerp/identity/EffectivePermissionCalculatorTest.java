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
    void keepsBroaderScopeWhenRoleGrantsWiderScopeThanGroup() {
        var permission = new Permission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.DELETE);

        var roleGroup = new PermissionGroup("Từ role - rộng", null);
        roleGroup.addItem(permission, IdentityConstants.PermissionScope.ORGANIZATION);
        var role = new Role(IdentityConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(roleGroup);

        var narrowGroup = new PermissionGroup("Từ group - hẹp", null);
        narrowGroup.addItem(permission, IdentityConstants.PermissionScope.PERSONAL);
        var group = new Group("Phòng ban", null);
        group.addPermissionGroup(narrowGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactly(
                new EffectivePermission(IdentityConstants.Resources.ACCOUNT, IdentityConstants.Actions.DELETE,
                        IdentityConstants.PermissionScope.ORGANIZATION));
    }

    @Test
    void unionsDistinctPermissionsFromRoleAndGroups() {
        var rolePermission = new Permission(IdentityConstants.Resources.BRANCH, IdentityConstants.Actions.READ);
        var roleGroup = new PermissionGroup("Từ role", null);
        roleGroup.addItem(rolePermission, IdentityConstants.PermissionScope.ORGANIZATION);
        var role = new Role(IdentityConstants.RoleCodes.ADMIN, "Admin", true);
        role.addPermissionGroup(roleGroup);

        var groupPermission = new Permission(IdentityConstants.Resources.GROUP, IdentityConstants.Actions.READ);
        var extraGroup = new PermissionGroup("Từ group", null);
        extraGroup.addItem(groupPermission, IdentityConstants.PermissionScope.BRANCH);
        var group = new Group("Phòng ban", null);
        group.addPermissionGroup(extraGroup);

        Set<EffectivePermission> result = calculator.calculate(role, Set.of(group));

        assertThat(result).containsExactlyInAnyOrder(
                new EffectivePermission(IdentityConstants.Resources.BRANCH, IdentityConstants.Actions.READ,
                        IdentityConstants.PermissionScope.ORGANIZATION),
                new EffectivePermission(IdentityConstants.Resources.GROUP, IdentityConstants.Actions.READ,
                        IdentityConstants.PermissionScope.BRANCH));
    }
}
