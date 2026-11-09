package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityException;
import com.eduerp.modules.identity.internal.access.AccountLifecycleGuard;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mở lại một tài khoản đang tạm đóng và đã từng kích hoạt. Tài khoản đã đóng thì không mở lại được. Tài khoản chưa kích hoạt lần nào không mở khoá ở đây: nó
 * cần một mật khẩu tạm mới, tức là "Gửi lại lời mời" ({@link ResendAccountInvite}).
 */
@Service
public class EnableAccount {

    private final AccountRepository accounts;
    private final AccountLifecycleGuard guard;
    private final ApplicationEventPublisher events;

    EnableAccount(AccountRepository accounts, AccountLifecycleGuard guard, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        guard.require(actorAccountId, account);
        if (account.isClosed()) {
            throw new IdentityException.AccountClosed(accountId);
        }
        if (account.getLastLogin() == null) {
            throw new IdentityException.AccountNeverActivated(accountId);
        }
        if (account.getStatus() == IdentityConstants.AccountStatus.ACTIVE) {
            return;
        }
        account.activate();
        events.publishEvent(new IdentityEvents.AccountEnabled(accountId, actorAccountId, actorBranchId));
    }
}
