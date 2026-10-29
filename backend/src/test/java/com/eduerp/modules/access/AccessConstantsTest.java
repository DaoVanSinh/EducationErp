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
    void courseCatalogWritesRequireOrganizationAndClassOperationsBranchScope() {
        assertThat(AccessConstants.AccessRules.CREATE_COURSE)
                .contains("COURSE", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_COURSE)
                .contains("COURSE", "READ", "@BRANCH");
        assertThat(AccessConstants.AccessRules.UPDATE_COURSE)
                .contains("COURSE", "UPDATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.CREATE_CLASS)
                .contains("CLASS", "CREATE", "@BRANCH");
        assertThat(AccessConstants.AccessRules.READ_CLASS)
                .contains("CLASS", "READ", "@BRANCH");
        assertThat(AccessConstants.AccessRules.UPDATE_CLASS)
                .contains("CLASS", "UPDATE", "@BRANCH");
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

    @Test
    void enrollmentAndInvoiceRulesAreBranchScopedButComboTiersStayOrganizationWide() {
        assertThat(AccessConstants.AccessRules.CREATE_ENROLLMENT)
                .contains("ENROLLMENT", "CREATE", "@BRANCH");
        assertThat(AccessConstants.AccessRules.READ_ENROLLMENT)
                .contains("ENROLLMENT", "READ", "@BRANCH");
        assertThat(AccessConstants.AccessRules.UPDATE_ENROLLMENT)
                .contains("ENROLLMENT", "UPDATE", "@BRANCH");
        assertThat(AccessConstants.AccessRules.CREATE_INVOICE)
                .contains("INVOICE", "CREATE", "@BRANCH");
        assertThat(AccessConstants.AccessRules.READ_INVOICE)
                .contains("INVOICE", "READ", "@BRANCH");
        assertThat(AccessConstants.AccessRules.UPDATE_INVOICE)
                .contains("INVOICE", "UPDATE", "@BRANCH");
        assertThat(AccessConstants.AccessRules.CREATE_COMBO_DISCOUNT_TIER)
                .contains("INVOICE", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_COMBO_DISCOUNT_TIER)
                .contains("INVOICE", "UPDATE", "@ORGANIZATION");
    }

    /** Phân hệ 3 không có luồng duyệt - không rule nào ở đây được đòi APPROVE (spec mục 7). */
    @Test
    void enrollmentAndInvoiceHaveNoApproveRule() {
        assertThat(AccessConstants.AccessRules.UPDATE_ENROLLMENT).doesNotContain(AccessConstants.Actions.APPROVE);
        assertThat(AccessConstants.AccessRules.UPDATE_INVOICE).doesNotContain(AccessConstants.Actions.APPROVE);
    }

    /**
     * Spec mục 6 bước 1: ba rule của GRADE KHÔNG mang marker {@code @ORGANIZATION}, khác MỌI rule
     * khác trong hệ thống. Thêm marker vào đây là chặn giáo viên ngay ở cửa
     * {@code @PreAuthorize} - họ chỉ có scope PERSONAL - và toàn bộ Phân hệ 4 trở thành chỉ admin
     * dùng được. Việc kiểm "lớp này có phải lớp của anh không" nằm ở
     * {@code ClassGradeAccessGuard}, không ở annotation.
     */
    @Test
    void gradeRulesDeliberatelyOmitTheMinimumOrganizationScopeMarker() {
        assertThat(AccessConstants.AccessRules.READ_GRADE)
                .contains("GRADE", "READ")
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.UPDATE_GRADE)
                .contains("GRADE", "UPDATE")
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.CREATE_GRADE)
                .contains("GRADE", "CREATE")
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
    }

    /** Resource GRADE phải là một tên tài nguyên thật, dùng chung giữa seed migration và guard. */
    @Test
    void gradeIsAKnownResourceName() {
        assertThat(AccessConstants.Resources.GRADE).isEqualTo("GRADE");
    }
}
