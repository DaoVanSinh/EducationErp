package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.internal.model.Branch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    Optional<Branch> findByCode(String code);
}
