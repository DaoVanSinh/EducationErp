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
    public PageResponse<ContractResponse> execute(Pageable pageable, UUID accountIdFilter) {
        Page<EmploymentContract> page = accountIdFilter == null ? contracts.findAll(pageable)
                : contracts.findAllByAccountId(accountIdFilter, pageable);
        var accountIds = page.getContent().stream().map(EmploymentContract::getAccountId).distinct().toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(contract -> CreateContract.toResponse(contract, accountInfo)));
    }
}
