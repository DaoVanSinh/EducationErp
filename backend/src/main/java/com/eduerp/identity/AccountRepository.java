package com.eduerp.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    long countByRole_Code(String roleCode);

    long countByHomeBranch_Id(UUID branchId);
}
