package com.eduerp.modules.identity.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.usecase.ListAccounts;
import com.eduerp.modules.identity.usecase.TransferAccountBranch;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phần của {@code /api/rbac/**} có Account là chủ thể — danh sách và chuyển chi nhánh. Phần thuần
 * RBAC (role/permission group/gán group) nằm ở
 * {@code com.eduerp.modules.access.web.RbacAdminController}, cùng tiền tố URL, khác module.
 */
@RestController
@RequestMapping("/api/rbac")
class AccountAdminController {

    private final ListAccounts listAccounts;
    private final TransferAccountBranch transferAccountBranch;

    AccountAdminController(ListAccounts listAccounts, TransferAccountBranch transferAccountBranch) {
        this.listAccounts = listAccounts;
        this.transferAccountBranch = transferAccountBranch;
    }

    /** {@code branchId} bỏ trống nghĩa là xem toàn tổ chức — hành vi gốc, không lọc gì. */
    @GetMapping("/accounts")
    @PreAuthorize(AccessConstants.AccessRules.READ_ACCOUNT)
    PageResponse<AccountSummaryResponse> listAccounts(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID branchId) {
        return listAccounts.execute(pageable, branchId);
    }

    @PostMapping("/accounts/{accountId}/transfer-branch")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ACCOUNT)
    void transferBranch(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID accountId,
            @Valid @RequestBody TransferBranchRequest request) {
        transferAccountBranch.execute(accountId, principal.accountId(), principal.homeBranchId(), request);
    }
}
