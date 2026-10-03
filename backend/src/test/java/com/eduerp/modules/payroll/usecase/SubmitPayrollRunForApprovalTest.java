package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitPayrollRunForApprovalTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final SubmitPayrollRunForApproval useCase = new SubmitPayrollRunForApproval(runs, payslips);

    @Test
    void rejectsSubmittingARunThatIsNotDraft() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PayrollRunNotEditableException.class);
    }

    /** Spec mục 3.3 bước 4: chặn submit khi còn Payslip CTV với hoursWorked == 0. */
    @Test
    void rejectsSubmittingWhenACollaboratorPayslipHasZeroHours() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(payslips.findAllByPayrollRun_Id(runId)).thenReturn(List.of(
                new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false)));

        assertThatThrownBy(() -> useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void submitsWhenAllCollaboratorPayslipsHaveHoursFilledIn() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(payslips.findAllByPayrollRun_Id(runId)).thenReturn(List.of(
                new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR,
                        new BigDecimal("3000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("20"), false),
                new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL, new BigDecimal("10000000"),
                        BigDecimal.ZERO, new BigDecimal("1050000"), new BigDecimal("2150000"), null, false)));

        useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.PENDING_APPROVAL);
    }
}
