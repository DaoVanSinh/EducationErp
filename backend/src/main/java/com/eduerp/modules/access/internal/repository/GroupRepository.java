package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.internal.model.Group;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, UUID> {
}
