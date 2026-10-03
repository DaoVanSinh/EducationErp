package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunAlreadyExistsException;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatePayrollRunTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final CreatePayrollRun useCase = new CreatePayrollRun(runs, payslips, contracts);

    /** Review Focus #4. */
    @Test
    void rejectsADuplicatePeriod() {
        when(runs.existsByYearAndMonth(2026, 1)).thenReturn(true);

        assertThatThrownBy(
                () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 1)))
                .isInstanceOf(PayrollRunAlreadyExistsException.class);
    }

    @Test
    void generatesOneDraftPayslipPerActiveContractOnlyWithZeroHoursForCollaborators() {
        var official = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2025, 1, 1), List.of());
        var collaborator = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR,
                null, new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());
        when(runs.existsByYearAndMonth(2026, 2)).thenReturn(false);
        when(runs.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contracts.findAllByStatus(PayrollConstants.ContractStatus.ACTIVE))
                .thenReturn(List.of(official, collaborator));
        when(payslips.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 2));

        assertThat(response.payslipCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(PayrollConstants.PayrollRunStatus.DRAFT);
        // Tổng gross = 10,000,000 (OFFICIAL, không thử việc) + 0 (COLLABORATOR, hoursWorked=0 khởi tạo).
        assertThat(response.totalGrossPay()).isEqualByComparingTo(new BigDecimal("10000000"));
    }

    /** Review Focus #5: usecase này không có dependency nào tới IdentityManagement - cấu trúc
     * constructor tự nó chứng minh CreatePayrollRun không thể truy vấn Account.status, chỉ lọc theo
     * EmploymentContract.status. Test này khoá chặt hành vi "vẫn tính lương dù tài khoản bị khoá". */
    @Test
    void generatesAPayslipRegardlessOfAnyAccountStatusBecauseItHasNoIdentityDependency() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2025, 1, 1), List.of());
        when(runs.existsByYearAndMonth(2026, 3)).thenReturn(false);
        when(runs.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contracts.findAllByStatus(PayrollConstants.ContractStatus.ACTIVE)).thenReturn(List.of(contract));
        when(payslips.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 3));

        assertThat(response.payslipCount()).isEqualTo(1);
    }
}
