package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.BranchCatalog;
import com.eduerp.modules.access.dto.RbacCatalogResponse;
import com.eduerp.modules.access.internal.model.PermissionGroup;
import com.eduerp.modules.access.internal.model.Role;
import com.eduerp.modules.access.internal.repository.AccountRoleAssignmentRepository;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionRepository;
import com.eduerp.modules.access.internal.repository.RoleHeadcountRow;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import com.eduerp.modules.access.internal.rules.AccessRules;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRbacCatalog {

    private final RoleRepository roles;
    private final PermissionGroupRepository permissionGroups;
    private final PermissionRepository permissions;
    private final AccountRoleAssignmentRepository accountRoles;
    private final BranchCatalog branches;
    private final AccessRules rules;

    GetRbacCatalog(RoleRepository roles, PermissionGroupRepository permissionGroups,
            PermissionRepository permissions, AccountRoleAssignmentRepository accountRoles, BranchCatalog branches,
            AccessRules rules) {
        this.roles = roles;
        this.permissionGroups = permissionGroups;
        this.permissions = permissions;
        this.accountRoles = accountRoles;
        this.branches = branches;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public RbacCatalogResponse execute() {
        Map<String, Long> headcounts = accountRoles.countAccountsByRole().stream()
                .collect(Collectors.toMap(RoleHeadcountRow::getRoleCode, RoleHeadcountRow::getAccountCount));
        return new RbacCatalogResponse(
                roles.findAll(Sort.by("code")).stream()
                        .map(role -> toRoleOption(role, headcounts.getOrDefault(role.getCode(), 0L)))
                        .toList(),
                permissionGroups.findAll(Sort.by("name")).stream().map(this::toPermissionGroupOption).toList(),
                branches.listAll(),
                permissions.findAll().stream()
                        .map(p -> new RbacCatalogResponse.PermissionOption(p.getId(), p.getResource(), p.getAction()))
                        .sorted(Comparator.comparing(RbacCatalogResponse.PermissionOption::resource)
                                .thenComparing(RbacCatalogResponse.PermissionOption::action))
                        .toList());
    }

    private RbacCatalogResponse.RoleOption toRoleOption(Role role, long accountCount) {
        var permissionGroupIds = role.getPermissionGroups().stream()
                .sorted(Comparator.comparing(PermissionGroup::getName))
                .map(PermissionGroup::getId)
                .toList();
        return new RbacCatalogResponse.RoleOption(role.getId(), role.getName(), role.getCode(),
                role.isSystemDefault(), rules.isLockedRole(role.getCode()), permissionGroupIds, accountCount);
    }

    private RbacCatalogResponse.PermissionGroupOption toPermissionGroupOption(PermissionGroup group) {
        var items = group.getItems().stream()
                .map(item -> new RbacCatalogResponse.PermissionGroupItemView(item.getPermission().getId(),
                        item.getPermission().getResource(), item.getPermission().getAction(), item.getScope()))
                .sorted(Comparator.comparing(RbacCatalogResponse.PermissionGroupItemView::resource)
                        .thenComparing(RbacCatalogResponse.PermissionGroupItemView::action))
                .toList();
        return new RbacCatalogResponse.PermissionGroupOption(group.getId(), group.getName(), group.getDescription(),
                rules.isLockedPermissionGroup(group.getId()), items);
    }
}
