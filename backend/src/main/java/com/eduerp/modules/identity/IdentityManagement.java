package com.eduerp.modules.identity;

import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module identity — type DUY NHẤT mà module khác được phép gọi (rule #1).
 */
@Service
public class IdentityManagement {

    private final AccountRepository accounts;

    IdentityManagement(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public AccountCounts accountCounts() {
        return new AccountCounts(accounts.count(),
                accounts.countByStatus(IdentityConstants.AccountStatus.ACTIVE),
                accounts.countByStatus(IdentityConstants.AccountStatus.DISABLED));
    }

    @Transactional(readOnly = true)
    public AccountCounts accountCounts(UUID branchId) {
        return new AccountCounts(accounts.countByHomeBranchId(branchId),
                accounts.countByHomeBranchIdAndStatus(branchId, IdentityConstants.AccountStatus.ACTIVE),
                accounts.countByHomeBranchIdAndStatus(branchId, IdentityConstants.AccountStatus.DISABLED));
    }

    /** Id của mọi account thuộc một chi nhánh — dùng để module access tự lọc role headcount theo chi nhánh đó. */
    @Transactional(readOnly = true)
    public List<UUID> accountIdsByBranch(UUID branchId) {
        return accounts.findIdsByHomeBranchId(branchId);
    }

    /** Email/tên của một loạt account theo id — tránh N+1 khi module khác cần hiển thị "ai đã làm việc này". */
    @Transactional(readOnly = true)
    public Map<UUID, AccountBasicInfo> summariesOf(Collection<UUID> accountIds) {
        return accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(a -> a.getId(),
                        a -> new AccountBasicInfo(a.getId(), a.getEmail(), a.getFullName())));
    }

    public record AccountCounts(long total, long active, long disabled) {
    }

    public record AccountBasicInfo(UUID id, String email, String fullName) {
    }
}
