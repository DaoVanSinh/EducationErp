package com.eduerp.modules.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lớp 2 của RBAC (spec 6.4): ORGANIZATION không lọc, BRANCH chỉ chi nhánh chủ quản, PERSONAL không thấy gì. */
class DataScopeTest {

    private static final String RESOURCE = AccessConstants.Resources.CLASS;
    private static final String ACTION = AccessConstants.Actions.READ;
    private final UUID homeBranch = UUID.randomUUID();
    private final UUID otherBranch = UUID.randomUUID();

    private DataScope scope(AccessConstants.PermissionScope scope, UUID home) {
        return new DataScope(RESOURCE, ACTION, scope, home);
    }

    @Test
    void organizationScopeCoversEveryBranchAndKeepsTheRequestedFilter() {
        var org = scope(AccessConstants.PermissionScope.ORGANIZATION, null);

        assertThat(org.covers(otherBranch)).isTrue();
        assertThat(org.branchFilter(null)).isNull();
        assertThat(org.branchFilter(otherBranch)).isEqualTo(otherBranch);
    }

    @Test
    void branchScopeCoversOnlyTheHomeBranchAndForcesTheFilter() {
        var branch = scope(AccessConstants.PermissionScope.BRANCH, homeBranch);

        assertThat(branch.covers(homeBranch)).isTrue();
        assertThat(branch.covers(otherBranch)).isFalse();
        assertThat(branch.branchFilter(null)).isEqualTo(homeBranch);
        assertThat(branch.branchFilter(otherBranch))
                .as("client gửi chi nhánh khác cũng bị ép về chi nhánh chủ quản")
                .isEqualTo(homeBranch);
        assertThatCode(() -> branch.requireCovers(homeBranch)).doesNotThrowAnyException();
        assertThatThrownBy(() -> branch.requireCovers(otherBranch))
                .isInstanceOf(AccessException.OutOfBranchScope.class);
    }

    @Test
    void branchScopeWithoutAHomeBranchSeesNothing() {
        var orphan = scope(AccessConstants.PermissionScope.BRANCH, null);

        assertThat(orphan.covers(homeBranch)).isFalse();
        assertThatThrownBy(() -> orphan.branchFilter(null)).isInstanceOf(AccessException.NoHomeBranch.class);
    }

    @Test
    void personalOrMissingScopeIsDeniedForAdministrativeData() {
        assertThat(scope(AccessConstants.PermissionScope.PERSONAL, homeBranch).covers(homeBranch)).isFalse();
        assertThatThrownBy(() -> scope(AccessConstants.PermissionScope.PERSONAL, homeBranch).branchFilter(null))
                .isInstanceOf(AccessException.ScopeDenied.class);
        assertThatThrownBy(() -> scope(null, null).requireCovers(homeBranch))
                .isInstanceOf(AccessException.ScopeDenied.class);
    }
}
