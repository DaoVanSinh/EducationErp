package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.internal.model.Group;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, UUID> {
}
