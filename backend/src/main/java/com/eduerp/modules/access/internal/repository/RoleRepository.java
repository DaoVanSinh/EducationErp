package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.internal.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);

    /** Vai trò đang chứa một nhóm quyền - sửa nhóm quyền đó là đổi quyền của mọi người giữ các vai trò này. */
    @Query("SELECT r.id FROM Role r JOIN r.permissionGroups pg WHERE pg.id = :permissionGroupId")
    List<UUID> findIdsByPermissionGroupId(UUID permissionGroupId);
}
