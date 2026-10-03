package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.UpdateContractRequest;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateContract {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;

    UpdateContract(EmploymentContractRepository contracts, IdentityManagement identity) {
        this.contracts = contracts;
        this.identity = identity;
    }

    @Transactional
    public ContractResponse execute(UUID contractId, UUID actorAccountId, UUID actorBranchId,
            UpdateContractRequest request) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        CreateContract.validateContractTerms(contract.getContractType(), request.baseSalary(), request.hourlyRate(),
                contract.getProbationStartDate(), request.probationEndDate());
        contract.setBaseSalary(request.baseSalary());
        contract.setHourlyRate(request.hourlyRate());
        contract.setProbationEndDate(request.probationEndDate());
        contract.setAllowances(
                request.allowances().stream().map(a -> new ContractAllowance(a.name(), a.amount())).toList());
        var accountInfo = identity.summariesOf(List.of(contract.getAccountId()));
        return CreateContract.toResponse(contract, accountInfo);
    }
}
