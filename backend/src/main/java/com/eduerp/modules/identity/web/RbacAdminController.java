package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.AssignGroupRequest;
import com.eduerp.modules.identity.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.identity.dto.CreateRoleRequest;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.usecase.AssignGroupToAccount;
import com.eduerp.modules.identity.usecase.CreatePermissionGroup;
import com.eduerp.modules.identity.usecase.CreateRole;
import com.eduerp.modules.identity.usecase.TransferAccountBranch;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rbac")
class RbacAdminController {

    private final CreateRole createRole;
    private final CreatePermissionGroup createPermissionGroup;
    private final AssignGroupToAccount assignGroupToAccount;
    private final TransferAccountBranch transferAccountBranch;

    RbacAdminController(CreateRole createRole, CreatePermissionGroup createPermissionGroup,
            AssignGroupToAccount assignGroupToAccount, TransferAccountBranch transferAccountBranch) {
        this.createRole = createRole;
        this.createPermissionGroup = createPermissionGroup;
        this.assignGroupToAccount = assignGroupToAccount;
        this.transferAccountBranch = transferAccountBranch;
    }

    @PostMapping("/permission-groups")
    @PreAuthorize(IdentityConstants.AccessRules.CREATE_PERMISSION_GROUP)
    UUID createPermissionGroup(@Valid @RequestBody CreatePermissionGroupRequest request) {
        return createPermissionGroup.execute(request);
    }

    @PostMapping("/roles")
    @PreAuthorize(IdentityConstants.AccessRules.CREATE_ROLE)
    UUID createRole(@Valid @RequestBody CreateRoleRequest request) {
        return createRole.execute(request);
    }

    @PostMapping("/accounts/{accountId}/groups")
    @PreAuthorize(IdentityConstants.AccessRules.UPDATE_ACCOUNT)
    void assignGroup(@PathVariable UUID accountId, @Valid @RequestBody AssignGroupRequest request) {
        assignGroupToAccount.execute(accountId, request);
    }

    @PostMapping("/accounts/{accountId}/transfer-branch")
    @PreAuthorize(IdentityConstants.AccessRules.UPDATE_ACCOUNT)
    void transferBranch(@PathVariable UUID accountId, @Valid @RequestBody TransferBranchRequest request) {
        transferAccountBranch.execute(accountId, request);
    }
}
