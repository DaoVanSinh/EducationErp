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

    @Test
    void courseAndClassRulesRequireOrganizationScope() {
        assertThat(AccessConstants.AccessRules.CREATE_COURSE)
                .contains("COURSE", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_COURSE)
                .contains("COURSE", "READ", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_COURSE)
                .contains("COURSE", "UPDATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.CREATE_CLASS)
                .contains("CLASS", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_CLASS)
                .contains("CLASS", "READ", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_CLASS)
                .contains("CLASS", "UPDATE", "@ORGANIZATION");
    }

    @Test
    void payrollRulesRequireOrganizationScope() {
        assertThat(AccessConstants.AccessRules.CREATE_PAYROLL)
                .contains("PAYROLL", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_PAYROLL)
                .contains("PAYROLL", "READ", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_PAYROLL)
                .contains("PAYROLL", "UPDATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.APPROVE_PAYROLL)
                .contains("PAYROLL", "APPROVE", "@ORGANIZATION");
    }
}
