package com.eduerp.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GroupRepository extends JpaRepository<Group, UUID> {
}
