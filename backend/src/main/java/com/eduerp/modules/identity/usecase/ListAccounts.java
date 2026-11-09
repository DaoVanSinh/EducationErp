package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.shared.PageResponse;
import java.util.List;
import java.util.Locale;
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

    /** Bộ lọc của màn Tài khoản. Trường null là không lọc theo trường đó. */
    public record Filter(UUID branchId, String roleCode, IdentityConstants.AccountStatus status, String query) {
    }

    /**
     * Role và tên chi nhánh không còn là quan hệ JPA trên {@code Account} (rule #3), nên được nạp theo lô cho cả
     * trang. Lọc theo vai trò lấy danh sách id từ module access rồi mới truy vấn, không JOIN xuyên module.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountSummaryResponse> execute(UUID actorAccountId, Pageable pageable, Filter filter) {
        var branchId = access.dataScopeOf(actorAccountId, AccessConstants.Resources.ACCOUNT,
                AccessConstants.Actions.READ).branchFilter(filter.branchId());
        var filterByRole = filter.roleCode() != null;
        List<UUID> roleAccountIds = filterByRole ? access.accountIdsWithRole(filter.roleCode()) : List.of();
        if (filterByRole && roleAccountIds.isEmpty()) {
            return PageResponse.of(Page.<AccountSummaryResponse>empty(pageable));
        }
        Page<Account> page = accounts.search(branchId, filter.status(), likePattern(filter.query()), filterByRole,
                filterByRole ? roleAccountIds : List.of(new UUID(0, 0)), pageable);
        var accountIds = page.getContent().stream().map(Account::getId).toList();
        var branchIds = page.getContent().stream().map(Account::getHomeBranchId).filter(Objects::nonNull).toList();

        var roles = access.rolesFor(accountIds);
        var branchNames = organization.namesOf(branchIds);

        return PageResponse.of(page.map(account -> toSummary(account, roles, branchNames)));
    }

    private static String likePattern(String query) {
        return query == null || query.isBlank() ? "%" : "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private static AccountSummaryResponse toSummary(Account account,
            Map<UUID, AccessManagement.RoleSummary> roles,
            Map<UUID, String> branchNames) {
        var role = roles.get(account.getId());
        var branchId = account.getHomeBranchId();
        return new AccountSummaryResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getStatus(), role == null ? null : role.code(),
                branchId, branchId == null ? null : branchNames.get(branchId),
                account.getLastLogin());
    }
}
