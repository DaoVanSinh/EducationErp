package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByResourceAndAction(String resource, String action);
}
