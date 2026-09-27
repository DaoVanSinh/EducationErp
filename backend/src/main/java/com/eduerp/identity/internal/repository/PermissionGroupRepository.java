package com.eduerp.identity.internal.repository;

import com.eduerp.identity.internal.model.PermissionGroup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionGroupRepository extends JpaRepository<PermissionGroup, UUID> {
}
