package com.eduerp.modules.access.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.dto.AssignGroupRequest;
import com.eduerp.modules.access.dto.ChangeAccountRoleRequest;
import com.eduerp.modules.access.dto.CreatePermissionGroupRequest;
import com.eduerp.modules.access.dto.CreateRoleRequest;
import com.eduerp.modules.access.dto.RbacCatalogResponse;
import com.eduerp.modules.access.dto.UpdatePermissionGroupRequest;
import com.eduerp.modules.access.dto.UpdateRoleRequest;
import com.eduerp.modules.access.usecase.AssignGroupToAccount;
import com.eduerp.modules.access.usecase.ChangeAccountRole;
import com.eduerp.modules.access.usecase.CreatePermissionGroup;
import com.eduerp.modules.access.usecase.CreateRole;
import com.eduerp.modules.access.usecase.GetRbacCatalog;
import com.eduerp.modules.access.usecase.UpdatePermissionGroup;
import com.eduerp.modules.access.usecase.UpdateRole;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phần thuần RBAC của {@code /api/rbac/**} (danh mục, tạo/sửa role và permission group, gán group và đổi
 * role của account). Danh sách account, khoá/mở khoá và chuyển chi nhánh nằm ở
 * {@code com.eduerp.modules.identity.web.AccountAdminController} — cùng tiền tố URL, khác module,
 * vì Account là chủ thể của các thao tác đó.
 */
@RestController
@RequestMapping("/api/rbac")
class RbacAdminController {

    private final CreateRole createRole;
    private final UpdateRole updateRole;
    private final CreatePermissionGroup createPermissionGroup;
    private final UpdatePermissionGroup updatePermissionGroup;
    private final AssignGroupToAccount assignGroupToAccount;
    private final ChangeAccountRole changeAccountRole;
    private final GetRbacCatalog getRbacCatalog;

    RbacAdminController(CreateRole createRole, UpdateRole updateRole, CreatePermissionGroup createPermissionGroup,
            UpdatePermissionGroup updatePermissionGroup, AssignGroupToAccount assignGroupToAccount,
            ChangeAccountRole changeAccountRole, GetRbacCatalog getRbacCatalog) {
        this.createRole = createRole;
        this.updateRole = updateRole;
        this.createPermissionGroup = createPermissionGroup;
        this.updatePermissionGroup = updatePermissionGroup;
        this.assignGroupToAccount = assignGroupToAccount;
        this.changeAccountRole = changeAccountRole;
        this.getRbacCatalog = getRbacCatalog;
    }

    @GetMapping("/catalog")
    @PreAuthorize(AccessConstants.AccessRules.READ_ROLE)
    RbacCatalogResponse catalog() {
        return getRbacCatalog.execute();
    }

    @PostMapping("/permission-groups")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_PERMISSION_GROUP)
    UUID createPermissionGroup(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreatePermissionGroupRequest request) {
        return createPermissionGroup.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PutMapping("/permission-groups/{permissionGroupId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PERMISSION_GROUP)
    void updatePermissionGroup(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID permissionGroupId, @Valid @RequestBody UpdatePermissionGroupRequest request) {
        updatePermissionGroup.execute(permissionGroupId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping("/roles")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_ROLE)
    UUID createRole(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateRoleRequest request) {
        return createRole.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ROLE)
    void updateRole(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        updateRole.execute(roleId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping("/accounts/{accountId}/groups")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ACCOUNT)
    void assignGroup(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID accountId,
            @Valid @RequestBody AssignGroupRequest request) {
        assignGroupToAccount.execute(accountId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/accounts/{accountId}/role")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ACCOUNT)
    void changeRole(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID accountId,
            @Valid @RequestBody ChangeAccountRoleRequest request) {
        changeAccountRole.execute(accountId, principal.accountId(), principal.homeBranchId(), request);
    }
}
