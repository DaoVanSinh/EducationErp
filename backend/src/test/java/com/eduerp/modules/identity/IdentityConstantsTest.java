package com.eduerp.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityConstantsTest {

    /** Hằng chuỗi dùng trong {@code @PreAuthorize} phải gọi đúng tên một scope thật. */
    @Test
    void scopeNameConstantsMatchTheEnum() {
        assertThat(IdentityConstants.ScopeNames.ORGANIZATION)
                .isEqualTo(IdentityConstants.PermissionScope.ORGANIZATION.name());
    }

    @Test
    void organizationIsBroaderThanBranchAndPersonal() {
        assertThat(IdentityConstants.PermissionScope.ORGANIZATION.rank())
                .isGreaterThan(IdentityConstants.PermissionScope.BRANCH.rank());
        assertThat(IdentityConstants.PermissionScope.BRANCH.rank())
                .isGreaterThan(IdentityConstants.PermissionScope.PERSONAL.rank());
    }
}
