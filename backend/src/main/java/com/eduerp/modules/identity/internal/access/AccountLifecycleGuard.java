package com.eduerp.modules.identity.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityException;
import com.eduerp.modules.identity.internal.model.Account;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ai được tạm đóng, mở lại, đóng một tài khoản: admin chi nhánh với tài khoản thuộc chi nhánh mình, admin gốc với mọi
 * tài khoản. Không ai đụng được admin gốc, tài khoản quản trị chi nhánh chỉ admin gốc xử lý, và không ai tự xử lý
 * chính mình.
 */
@Component
public class AccountLifecycleGuard {

    private final AccessManagement access;

    AccountLifecycleGuard(AccessManagement access) {
        this.access = access;
    }

    public void require(UUID actorAccountId, Account target) {
        if (target.getId().equals(actorAccountId)) {
            throw new IdentityException.CannotModifyOwnAccount();
        }
        var scope = access.dataScopeOf(actorAccountId, AccessConstants.Resources.ACCOUNT,
                AccessConstants.Actions.UPDATE);
        scope.requireCovers(target.getHomeBranchId());
        var targetRole = access.roleOf(target.getId()).map(AccessManagement.RoleSummary::code).orElse(null);
        if (AccessConstants.RoleCodes.ADMIN.equals(targetRole)) {
            throw new IdentityException.AccountProtected("Tài khoản admin gốc không thể bị tạm đóng hoặc đóng");
        }
        if (AccessConstants.RoleCodes.BRANCH_ADMIN.equals(targetRole) && !scope.isOrganizationWide()) {
            throw new IdentityException.AccountProtected("Chỉ admin gốc xử lý được tài khoản quản trị chi nhánh");
        }
    }
}
