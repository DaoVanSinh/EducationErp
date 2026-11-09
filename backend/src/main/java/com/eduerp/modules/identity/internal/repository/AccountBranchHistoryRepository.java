package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.internal.model.AccountBranchHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBranchHistoryRepository extends JpaRepository<AccountBranchHistory, UUID> {
    List<AccountBranchHistory> findAllByAccountIdOrderByChangedAtDesc(UUID accountId);
}
