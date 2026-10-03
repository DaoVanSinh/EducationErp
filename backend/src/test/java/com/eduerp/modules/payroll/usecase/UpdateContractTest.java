package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.UpdateContractRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Review findings Critical #1: UpdateContract phải chạy cùng ràng buộc với CreateContract, không
 * được để hợp đồng OFFICIAL mất baseSalary hay CTV có lại ngày thử việc sau khi sửa. */
class UpdateContractTest {

    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final IdentityManagement identity = mock(IdentityManagement.class);
    private final UpdateContract useCase = new UpdateContract(contracts, identity);

    @Test
    void rejectsClearingBaseSalaryOnAnOfficialContract() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        var contractId = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(contractId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdateContractRequest(null, null, null, List.of())))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void rejectsClearingHourlyRateOnACollaboratorContract() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2026, 1, 1), List.of());
        var contractId = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(contractId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdateContractRequest(null, null, null, List.of())))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    /** Review Focus #2, qua đường update: CTV không được có lại thử việc sau khi sửa. */
    @Test
    void rejectsAddingAProbationEndDateToACollaboratorContract() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2026, 1, 1), List.of());
        var contractId = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(contractId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdateContractRequest(null, new BigDecimal("150000"), LocalDate.of(2026, 3, 1), List.of())))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void updatesAnOfficialContractWithAValidBaseSalary() {
        var accountId = UUID.randomUUID();
        var contract = new EmploymentContract(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        var contractId = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(identity.summariesOf(List.of(accountId)))
                .thenReturn(Map.of(accountId, new IdentityManagement.AccountBasicInfo(accountId, "a@eduerp.local", "A")));

        var response = useCase.execute(contractId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdateContractRequest(new BigDecimal("12000000"), null, null, List.of()));

        assertThat(response.baseSalary()).isEqualTo(new BigDecimal("12000000"));
    }
}
