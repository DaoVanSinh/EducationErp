package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.dto.AccountSummaryResponse;
import com.eduerp.modules.identity.dto.NamedReference;
import com.eduerp.modules.identity.dto.PageResponse;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.Comparator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAccounts {

    private final AccountRepository accounts;

    ListAccounts(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * {@code readOnly} và còn trong transaction khi map — các quan hệ lazy (role, chi nhánh, group)
     * chỉ được đụng tới ở đây, nên tầng web không bao giờ gặp một proxy đã đóng session.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountSummaryResponse> execute(Pageable pageable) {
        return PageResponse.of(accounts.findAll(pageable).map(ListAccounts::toSummary));
    }

    private static AccountSummaryResponse toSummary(Account account) {
        var branch = account.getHomeBranch();
        return new AccountSummaryResponse(account.getId(), account.getEmail(), account.getFullName(),
                account.getStatus(), account.getRole().getCode(),
                branch == null ? null : branch.getId(), branch == null ? null : branch.getName(),
                account.getGroups().stream()
                        .map(group -> new NamedReference(group.getId(), group.getName()))
                        .sorted(Comparator.comparing(NamedReference::name))
                        .toList());
    }
}
