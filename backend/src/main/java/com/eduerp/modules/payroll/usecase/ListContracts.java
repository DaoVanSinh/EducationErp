package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListContracts {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;

    ListContracts(EmploymentContractRepository contracts, IdentityManagement identity) {
        this.contracts = contracts;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> execute(Pageable pageable, UUID accountIdFilter, UUID branchId) {
        Page<EmploymentContract> page = pageOf(pageable, accountIdFilter, branchId);
        var accountIds = page.getContent().stream().map(EmploymentContract::getAccountId).distinct().toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(contract -> CreateContract.toResponse(contract, accountInfo)));
    }

    /** Hợp đồng không gắn chi nhánh, nên lọc theo chi nhánh chủ quản của nhân sự. Chọn một người thì không lọc thêm. */
    private Page<EmploymentContract> pageOf(Pageable pageable, UUID accountIdFilter, UUID branchId) {
        if (accountIdFilter != null) {
            return contracts.findAllByAccountId(accountIdFilter, pageable);
        }
        if (branchId == null) {
            return contracts.findAll(pageable);
        }
        var accountIds = identity.accountIdsByBranch(branchId);
        return accountIds.isEmpty() ? Page.empty(pageable) : contracts.findAllByAccountIdIn(accountIds, pageable);
    }
}
