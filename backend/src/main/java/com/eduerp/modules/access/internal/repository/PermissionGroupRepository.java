package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.internal.model.PermissionGroup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID> {
}
