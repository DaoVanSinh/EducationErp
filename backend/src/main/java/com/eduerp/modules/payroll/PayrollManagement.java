package com.eduerp.modules.payroll;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.usecase.CreateContract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Facade của module payroll - type duy nhất module khác được phép gọi (rule #1). Chưa module nào
 * gọi tới ở phase này (giống CoursesManagement/TeachersManagement) - method này là điểm bắt đầu
 * tối thiểu, không suy đoán thêm method nào khác chưa ai cần. */
@Service
public class PayrollManagement {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;

    PayrollManagement(EmploymentContractRepository contracts, IdentityManagement identity) {
        this.contracts = contracts;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public Optional<ContractResponse> getActiveContractFor(UUID accountId) {
        return contracts.findByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE)
                .map(contract -> CreateContract.toResponse(contract, identity.summariesOf(List.of(accountId))));
    }
}
