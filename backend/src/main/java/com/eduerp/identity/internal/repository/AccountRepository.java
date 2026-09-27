package com.eduerp.identity.internal.repository;

import com.eduerp.identity.internal.model.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    long countByRole_Code(String roleCode);

    long countByHomeBranch_Id(UUID branchId);
}
