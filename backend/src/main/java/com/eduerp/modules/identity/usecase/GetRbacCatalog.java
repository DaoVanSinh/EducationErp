package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.dto.NamedReference;
import com.eduerp.modules.identity.dto.RbacCatalogResponse;
import com.eduerp.modules.identity.internal.repository.BranchRepository;
import com.eduerp.modules.identity.internal.repository.GroupRepository;
import com.eduerp.modules.identity.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.identity.internal.repository.PermissionRepository;
import com.eduerp.modules.identity.internal.repository.RoleRepository;
import java.util.Comparator;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRbacCatalog {

    private final RoleRepository roles;
    private final GroupRepository groups;
    private final PermissionGroupRepository permissionGroups;
    private final BranchRepository branches;
    private final PermissionRepository permissions;

    GetRbacCatalog(RoleRepository roles, GroupRepository groups, PermissionGroupRepository permissionGroups,
            BranchRepository branches, PermissionRepository permissions) {
        this.roles = roles;
        this.groups = groups;
        this.permissionGroups = permissionGroups;
        this.branches = branches;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public RbacCatalogResponse execute() {
        return new RbacCatalogResponse(
                roles.findAll(Sort.by("code")).stream()
                        .map(role -> new NamedReference(role.getId(), role.getName())).toList(),
                groups.findAll(Sort.by("name")).stream()
                        .map(group -> new NamedReference(group.getId(), group.getName())).toList(),
                permissionGroups.findAll(Sort.by("name")).stream()
                        .map(pg -> new NamedReference(pg.getId(), pg.getName())).toList(),
                branches.findAll(Sort.by("code")).stream()
                        .map(branch -> new NamedReference(branch.getId(), branch.getName())).toList(),
                permissions.findAll().stream()
                        .map(p -> new RbacCatalogResponse.PermissionOption(p.getId(), p.getResource(), p.getAction()))
                        .sorted(Comparator.comparing(RbacCatalogResponse.PermissionOption::resource)
                                .thenComparing(RbacCatalogResponse.PermissionOption::action))
                        .toList());
    }
}
