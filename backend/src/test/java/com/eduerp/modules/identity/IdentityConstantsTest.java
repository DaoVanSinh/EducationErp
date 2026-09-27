package com.eduerp.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityConstantsTest {

    @Test
    void organizationIsBroaderThanBranchAndPersonal() {
        assertThat(IdentityConstants.PermissionScope.ORGANIZATION.rank())
                .isGreaterThan(IdentityConstants.PermissionScope.BRANCH.rank());
        assertThat(IdentityConstants.PermissionScope.BRANCH.rank())
                .isGreaterThan(IdentityConstants.PermissionScope.PERSONAL.rank());
    }
}
