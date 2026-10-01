package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.shared.NamedReference;
import com.eduerp.shared.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAccounts {

    private final AccountRepository accounts;
    private final AccessManagement access;
    private final OrganizationManagement organization;

    ListAccounts(AccountRepository accounts, AccessManagement access, OrganizationManagement organization) {
        this.accounts = accounts;
        this.access = access;
        this.organization = organization;
    }

    /**
     * Role, group và tên chi nhánh không còn là quan hệ JPA trên {@code Account} (rule #3), nên
     * được nạp theo lô cho cả trang thay vì từng account một — tránh N+1 khi ghép ba module lại.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountSummaryResponse> execute(Pageable pageable, UUID branchId) {
        Page<Account> page = branchId == null ? accounts.findAll(pageable)
                : accounts.findAllByHomeBranchId(branchId, pageable);
        var accountIds = page.getContent().stream().map(Account::getId).toList();
        var branchIds = page.getContent().stream().map(Account::getHomeBranchId).filter(Objects::nonNull).toList();

        var roles = access.rolesFor(accountIds);
        var groups = access.groupsFor(accountIds);
        var branchNames = organization.namesOf(branchIds);

        return PageResponse.of(page.map(account -> toSummary(account, roles, groups, branchNames)));
    }

    private static AccountSummaryResponse toSummary(Account account,
            Map<UUID, AccessManagement.RoleSummary> roles,
            Map<UUID, List<NamedReference>> groups,
            Map<UUID, String> branchNames) {
        var role = roles.get(account.getId());
        var branchId = account.getHomeBranchId();
        return new AccountSummaryResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getStatus(), role == null ? null : role.code(),
                branchId, branchId == null ? null : branchNames.get(branchId),
                groups.getOrDefault(account.getId(), List.of()));
    }
}
