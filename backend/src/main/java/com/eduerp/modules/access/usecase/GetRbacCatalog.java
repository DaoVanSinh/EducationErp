package com.eduerp.modules.access.usecase;

import com.eduerp.modules.access.BranchCatalog;
import com.eduerp.modules.access.dto.RbacCatalogResponse;
import com.eduerp.modules.access.internal.repository.GroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionGroupRepository;
import com.eduerp.modules.access.internal.repository.PermissionRepository;
import com.eduerp.modules.access.internal.repository.RoleRepository;
import com.eduerp.shared.NamedReference;
import java.util.Comparator;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRbacCatalog {

    private final RoleRepository roles;
    private final GroupRepository groups;
    private final PermissionGroupRepository permissionGroups;
    private final PermissionRepository permissions;
    private final BranchCatalog branches;

    GetRbacCatalog(RoleRepository roles, GroupRepository groups, PermissionGroupRepository permissionGroups,
            PermissionRepository permissions, BranchCatalog branches) {
        this.roles = roles;
        this.groups = groups;
        this.permissionGroups = permissionGroups;
        this.permissions = permissions;
        this.branches = branches;
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
                branches.listAll(),
                permissions.findAll().stream()
                        .map(p -> new RbacCatalogResponse.PermissionOption(p.getId(), p.getResource(), p.getAction()))
                        .sorted(Comparator.comparing(RbacCatalogResponse.PermissionOption::resource)
                                .thenComparing(RbacCatalogResponse.PermissionOption::action))
                        .toList());
    }
}
