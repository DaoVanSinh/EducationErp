package com.eduerp.modules.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessConstantsTest {

    /** Hằng chuỗi dùng trong {@code @PreAuthorize} phải gọi đúng tên một scope thật. */
    @Test
    void scopeNameConstantsMatchTheEnum() {
        assertThat(AccessConstants.ScopeNames.ORGANIZATION)
                .isEqualTo(AccessConstants.PermissionScope.ORGANIZATION.name());
    }

    @Test
    void organizationIsBroaderThanBranchAndPersonal() {
        assertThat(AccessConstants.PermissionScope.ORGANIZATION.rank())
                .isGreaterThan(AccessConstants.PermissionScope.BRANCH.rank());
        assertThat(AccessConstants.PermissionScope.BRANCH.rank())
                .isGreaterThan(AccessConstants.PermissionScope.PERSONAL.rank());
    }
}
