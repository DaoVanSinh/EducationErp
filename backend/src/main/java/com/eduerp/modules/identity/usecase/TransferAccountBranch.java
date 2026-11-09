package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.AccountLifecycleParticipant;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityEvents;
import com.eduerp.modules.identity.IdentityException;
import com.eduerp.modules.identity.ReferenceNotFoundException;
import com.eduerp.modules.identity.dto.TransferBranchRequest;
import com.eduerp.modules.identity.internal.model.AccountBranchHistory;
import com.eduerp.modules.identity.internal.repository.AccountBranchHistoryRepository;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Chỉ admin gốc chuyển chi nhánh (rule ở controller). Giáo viên phải bàn giao hết lớp đang mở trước, vì mỗi giáo viên
 * chỉ dạy lớp của chi nhánh chủ quản. Dữ liệu cũ giữ nguyên chi nhánh cũ, lần chuyển được ghi vào lịch sử.
 */
@Service
public class TransferAccountBranch {

    private final AccountRepository accounts;
    private final AccountBranchHistoryRepository history;
    private final OrganizationManagement organization;
    private final AccessManagement access;
    private final ObjectProvider<AccountLifecycleParticipant> participants;
    private final ApplicationEventPublisher events;

    TransferAccountBranch(AccountRepository accounts, AccountBranchHistoryRepository history,
            OrganizationManagement organization, AccessManagement access,
            ObjectProvider<AccountLifecycleParticipant> participants, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.history = history;
        this.organization = organization;
        this.access = access;
        this.participants = participants;
        this.events = events;
    }

    /**
     * Chi nhánh quyết định phạm vi mà quyền scope BRANCH nhìn thấy, nên chuyển chi nhánh cũng phải
     * xoá cache quyền như khi đổi group. Evict + audit SAU commit (rule #6): rollback thì không để
     * lại dấu vết cho một thay đổi chưa từng thật sự xảy ra.
     */
    @Transactional
    public void execute(UUID accountId, UUID actorAccountId, UUID actorBranchId, TransferBranchRequest request) {
        var account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.isClosed()) {
            throw new IdentityException.AccountClosed(accountId);
        }
        if (!organization.exists(request.branchId())) {
            throw new ReferenceNotFoundException(AccessConstants.Resources.BRANCH, request.branchId());
        }
        if (request.branchId().equals(account.getHomeBranchId())) {
            return;
        }
        participants.orderedStream()
                .forEach(participant -> participant.beforeBranchTransfer(accountId, request.branchId()));
        history.save(new AccountBranchHistory(accountId, account.getHomeBranchId(), request.branchId(),
                actorAccountId));
        account.transferToBranch(request.branchId());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                access.evictPermissionCache(accountId);
                events.publishEvent(new IdentityEvents.AccountBranchTransferred(accountId, request.branchId(),
                        actorAccountId, actorBranchId));
            }
        });
    }
}
