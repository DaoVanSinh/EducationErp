package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
}
