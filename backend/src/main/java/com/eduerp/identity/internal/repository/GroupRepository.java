package com.eduerp.identity.internal.repository;

import com.eduerp.identity.internal.model.Group;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, UUID> {
}
