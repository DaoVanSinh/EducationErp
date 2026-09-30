package com.eduerp.modules.organization.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.organization.dto.BranchResponse;
import com.eduerp.modules.organization.dto.CreateBranchRequest;
import com.eduerp.modules.organization.dto.UpdateBranchRequest;
import com.eduerp.modules.organization.usecase.CreateBranch;
import com.eduerp.modules.organization.usecase.ListBranches;
import com.eduerp.modules.organization.usecase.UpdateBranch;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quản trị chi nhánh — không có endpoint xoá: chi nhánh chỉ được vô hiệu hoá qua {@code active}. */
@RestController
@RequestMapping("/api/organization/branches")
class BranchAdminController {

    private final ListBranches listBranches;
    private final CreateBranch createBranch;
    private final UpdateBranch updateBranch;

    BranchAdminController(ListBranches listBranches, CreateBranch createBranch, UpdateBranch updateBranch) {
        this.listBranches = listBranches;
        this.createBranch = createBranch;
        this.updateBranch = updateBranch;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_BRANCH)
    PageResponse<BranchResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listBranches.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_BRANCH)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateBranchRequest request) {
        return createBranch.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{branchId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_BRANCH)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest request) {
        updateBranch.execute(branchId, principal.accountId(), principal.homeBranchId(), request);
    }
}
