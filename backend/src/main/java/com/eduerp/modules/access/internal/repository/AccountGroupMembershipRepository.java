package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.internal.model.AccountGroupMembership;
import com.eduerp.modules.access.internal.model.Group;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountGroupMembershipRepository extends JpaRepository<AccountGroupMembership, AccountGroupMembership.Key> {

    @Query("SELECT m.group FROM AccountGroupMembership m WHERE m.id.accountId = :accountId")
    List<Group> findGroupsByAccountId(UUID accountId);

    @Query("SELECT m FROM AccountGroupMembership m WHERE m.id.accountId IN :accountIds")
    List<AccountGroupMembership> findByAccountIdIn(Collection<UUID> accountIds);
}
