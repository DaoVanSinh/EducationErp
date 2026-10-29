package com.eduerp.modules.identity;

import com.eduerp.modules.identity.dto.CreateAccountRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.usecase.CreateAccount;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final CreateAccount createAccount;

    IdentityManagement(AccountRepository accounts, CreateAccount createAccount) {
        this.accounts = accounts;
        this.createAccount = createAccount;
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

    /** Chi nhánh chủ quản của một tài khoản - để module khác kiểm phạm vi chi nhánh của bản ghi gắn với nó. */
    @Transactional(readOnly = true)
    public Optional<UUID> homeBranchOf(UUID accountId) {
        return accounts.findById(accountId).map(Account::getHomeBranchId);
    }

    /** Email/tên của một loạt account theo id — tránh N+1 khi module khác cần hiển thị "ai đã làm việc này". */
    @Transactional(readOnly = true)
    public Map<UUID, AccountBasicInfo> summariesOf(Collection<UUID> accountIds) {
        return accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(a -> a.getId(),
                        a -> new AccountBasicInfo(a.getId(), a.getEmail(), a.getFullName())));
    }

    /**
     * Tạo tài khoản + gửi email mời, dùng cho các luồng "tạo một bước" ở module khác (học viên, giáo viên):
     * cùng một đường với màn Tài khoản (kiểm email trùng, gán vai trò, mật khẩu tạm 7 ngày), nên tài khoản
     * sinh ra ở đâu cũng đăng nhập lần đầu theo cùng một luật. Chạy trong transaction của nơi gọi.
     */
    @Transactional
    public UUID inviteAccount(String email, String fullName, UUID homeBranchId, UUID roleId, UUID actorAccountId,
            UUID actorBranchId) {
        return createAccount.execute(actorAccountId, actorBranchId,
                new CreateAccountRequest(email, fullName, homeBranchId, roleId));
    }

    public record AccountCounts(long total, long active, long disabled) {
    }

    public record AccountBasicInfo(UUID id, String email, String fullName) {
    }
}
