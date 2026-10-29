package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.SessionRevocationService;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buộc một tài khoản đăng xuất khỏi mọi thiết bị mà không khoá tài khoản (spec 6.3 Force-logout) - vd
 * nghi lộ mật khẩu. Người dùng vẫn đăng nhập lại được bằng mật khẩu của mình.
 */
@Service
public class SignOutEverywhere {

    private final AccountRepository accounts;
    private final SessionRevocationService revocations;
    private final ApplicationEventPublisher events;

    SignOutEverywhere(AccountRepository accounts, SessionRevocationService revocations,
            ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.revocations = revocations;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        revocations.revokeAll(accountId);
        events.publishEvent(new IdentityEvents.AccountSignedOutEverywhere(accountId, actorAccountId, actorBranchId));
    }
}
