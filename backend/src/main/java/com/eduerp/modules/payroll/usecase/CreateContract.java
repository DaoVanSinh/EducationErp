package com.eduerp.modules.payroll.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractAlreadyActiveException;
import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.dto.AllowanceResponse;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateContract {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;
    private final ApplicationEventPublisher events;

    CreateContract(EmploymentContractRepository contracts, IdentityManagement identity,
            ApplicationEventPublisher events) {
        this.contracts = contracts;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public ContractResponse execute(UUID actorAccountId, UUID actorBranchId, CreateContractRequest request) {
        var accountInfo = identity.summariesOf(List.of(request.accountId()));
        if (!accountInfo.containsKey(request.accountId())) {
            throw new AppValidationException("accountId", "Tài khoản không tồn tại");
        }
        if (contracts.existsByAccountIdAndStatus(request.accountId(), PayrollConstants.ContractStatus.ACTIVE)) {
            throw new ContractAlreadyActiveException(request.accountId());
        }
        validateContractTerms(request.contractType(), request.baseSalary(), request.hourlyRate(),
                request.probationStartDate(), request.probationEndDate());

        var allowances = request.allowances().stream().map(a -> new ContractAllowance(a.name(), a.amount())).toList();
        var saved = contracts.save(new EmploymentContract(request.accountId(), request.contractType(),
                request.baseSalary(), request.hourlyRate(), request.probationStartDate(),
                request.probationEndDate(), request.startDate(), allowances));
        events.publishEvent(new PayrollEvents.ContractCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved, accountInfo);
    }

    private static void validateContractTerms(PayrollConstants.ContractType type, BigDecimal baseSalary,
            BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate) {
        if (type == PayrollConstants.ContractType.OFFICIAL) {
            if (baseSalary == null) {
                throw new InvalidContractTermsException("Hợp đồng chính thức phải có baseSalary");
            }
        } else {
            if (hourlyRate == null) {
                throw new InvalidContractTermsException("Hợp đồng CTV phải có hourlyRate");
            }
            if (probationStartDate != null || probationEndDate != null) {
                throw new InvalidContractTermsException("Hợp đồng CTV không áp dụng thử việc");
            }
        }
    }

    public static ContractResponse toResponse(EmploymentContract contract,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(contract.getAccountId());
        var allowances = contract.getAllowances().stream()
                .map(a -> new AllowanceResponse(a.getName(), a.getAmount())).toList();
        return new ContractResponse(contract.getId(), contract.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                contract.getContractType(), contract.getStatus(), contract.getBaseSalary(), contract.getHourlyRate(),
                contract.getProbationStartDate(), contract.getProbationEndDate(), contract.getStartDate(),
                contract.getEndDate(), allowances, contract.getContractFileKey());
    }
}
