package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.internal.audit.Audited;
import com.eduerp.modules.identity.internal.permission.PermissionCacheService;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.BranchRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferAccountBranch {

    private final AccountRepository accounts;
    private final BranchRepository branches;
    private final PermissionCacheService permissionCache;

    TransferAccountBranch(AccountRepository accounts, BranchRepository branches,
            PermissionCacheService permissionCache) {
        this.accounts = accounts;
        this.branches = branches;
        this.permissionCache = permissionCache;
    }

    /**
     * Chi nhánh quyết định phạm vi mà quyền scope BRANCH nhìn thấy, nên chuyển chi nhánh cũng phải
     * xoá cache quyền như khi đổi group.
     */
    @Transactional
    @Audited(action = IdentityConstants.AuditActions.ACCOUNT_TRANSFER_BRANCH,
            entityType = IdentityConstants.Resources.ACCOUNT)
    public void execute(UUID accountId, TransferBranchRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        var branch = branches.findById(request.branchId()).orElseThrow(
                () -> new ReferenceNotFoundException(IdentityConstants.Resources.BRANCH, request.branchId()));
        account.transferToBranch(branch);
        permissionCache.evict(accountId);
    }
}
