package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.internal.model.PermissionGroup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID> {
}
