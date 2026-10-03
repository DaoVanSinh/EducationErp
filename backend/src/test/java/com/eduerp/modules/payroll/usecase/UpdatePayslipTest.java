package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdatePayslipTest {

    private final PayslipRepository payslipRepository = mock(PayslipRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final UpdatePayslip useCase = new UpdatePayslip(payslipRepository, contracts);

    @Test
    void rejectsEditingAPayslipWhenRunIsNotDraft() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var payslip = new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        var payslipId = UUID.randomUUID();
        when(payslipRepository.findById(payslipId)).thenReturn(Optional.of(payslip));

        assertThatThrownBy(() -> useCase.execute(payslipId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePayslipRequest(new BigDecimal("10"), BigDecimal.ZERO, null)))
                .isInstanceOf(PayrollRunNotEditableException.class);
    }

    @Test
    void recomputesGrossPayAndInsuranceWhenCollaboratorHoursChange() {
        var run = new PayrollRun(2026, 1);
        var accountId = UUID.randomUUID();
        var payslip = new Payslip(run, accountId, PayrollConstants.ContractType.COLLABORATOR, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        var contract = new EmploymentContract(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());
        var payslipId = UUID.randomUUID();
        when(payslipRepository.findById(payslipId)).thenReturn(Optional.of(payslip));
        when(contracts.findByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));

        var response = useCase.execute(payslipId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePayslipRequest(new BigDecimal("20"), new BigDecimal("100000"), "Đã dạy đủ giờ"));

        assertThat(response.grossPay()).isEqualByComparingTo(new BigDecimal("150000").multiply(new BigDecimal("20")));
        assertThat(response.hoursWorked()).isEqualTo(new BigDecimal("20"));
        assertThat(response.incomeTaxWithheld()).isEqualTo(new BigDecimal("100000"));
    }

    /** Review finding Important #5: note=null trong request nghĩa là "không đổi", không phải "xoá ghi
     * chú" - giống hệt cách hoursWorked/incomeTaxWithheld null đã được xử lý trong method này. Frontend
     * luôn gửi note: null khi chỉ sửa giờ dạy hoặc thuế, nên trước khi sửa, mỗi lần sửa một ô sẽ xoá
     * mất ghi chú đã nhập trước đó. */
    @Test
    void doesNotClearAnExistingNoteWhenRequestNoteIsNull() {
        var run = new PayrollRun(2026, 1);
        var payslip = new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        payslip.setNote("Đã xác nhận với nhân viên");
        var payslipId = UUID.randomUUID();
        when(payslipRepository.findById(payslipId)).thenReturn(Optional.of(payslip));

        useCase.execute(payslipId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePayslipRequest(null, new BigDecimal("50000"), null));

        assertThat(payslip.getNote()).isEqualTo("Đã xác nhận với nhân viên");
    }
}
