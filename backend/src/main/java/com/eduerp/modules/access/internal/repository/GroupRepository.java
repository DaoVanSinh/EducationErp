package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.internal.model.Group;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    @Query("SELECT g.id FROM Group g JOIN g.permissionGroups pg WHERE pg.id = :permissionGroupId")
    List<UUID> findIdsByPermissionGroupId(UUID permissionGroupId);
}
