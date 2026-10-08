package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.ContractAlreadyTerminatedException;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminateContract {

    private final EmploymentContractRepository contracts;
    private final ApplicationEventPublisher events;

    TerminateContract(EmploymentContractRepository contracts, ApplicationEventPublisher events) {
        this.contracts = contracts;
        this.events = events;
    }

    @Transactional
    public void execute(UUID contractId, UUID actorAccountId, UUID actorBranchId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        if (contract.getStatus() == PayrollConstants.ContractStatus.TERMINATED) {
            throw new ContractAlreadyTerminatedException(contractId);
        }
        contract.terminate();
        events.publishEvent(new PayrollEvents.ContractTerminated(contractId, actorAccountId, actorBranchId));
    }
}
