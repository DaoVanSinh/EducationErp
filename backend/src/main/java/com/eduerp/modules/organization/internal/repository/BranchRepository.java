package com.eduerp.modules.organization.internal.repository;

import com.eduerp.modules.organization.internal.model.Branch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    Optional<Branch> findByCode(String code);
}
