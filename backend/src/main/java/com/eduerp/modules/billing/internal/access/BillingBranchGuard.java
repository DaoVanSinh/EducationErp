package com.eduerp.modules.billing.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lớp 2 của RBAC (spec 6.4) cho hoá đơn và combo: scope ORGANIZATION mọi chi nhánh, scope BRANCH chỉ chứng
 * từ của chi nhánh chủ quản. Một chỗ duy nhất để mọi use case của billing hỏi cùng một câu.
 */
@Component
public class BillingBranchGuard {

    private final AccessManagement access;

    BillingBranchGuard(AccessManagement access) {
        this.access = access;
    }

    /** Ném 403 nếu chứng từ thuộc chi nhánh ngoài phạm vi của người gọi cho {@code action}. */
    public void require(UUID actorAccountId, String action, UUID branchId) {
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.INVOICE, action).requireCovers(branchId);
    }

    /** Chi nhánh để lọc danh sách: {@code null} = không lọc (toàn tổ chức). */
    public UUID listFilter(UUID actorAccountId) {
        return access.dataScopeOf(actorAccountId, AccessConstants.Resources.INVOICE, AccessConstants.Actions.READ)
                .branchFilter(null);
    }
}
