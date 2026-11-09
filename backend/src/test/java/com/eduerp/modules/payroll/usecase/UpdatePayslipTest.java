package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.PayslipNotFoundException;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UpdatePayslipTest {

    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final UpdatePayslip useCase = new UpdatePayslip(payslips);

    private final UUID runId = UUID.randomUUID();
    private final UUID payslipId = UUID.randomUUID();
    private PayrollRun run;

    @BeforeEach
    void draftRun() {
        run = new PayrollRun(2026, 10);
        ReflectionTestUtils.setField(run, "id", runId);
    }

    private Payslip givenHourlyPayslip(long grossPay) {
        var payslip = PayslipFixtures.hourly(run, UUID.randomUUID(), grossPay, 10);
        when(payslips.findById(payslipId)).thenReturn(Optional.of(payslip));
        return payslip;
    }

    private void update(UUID inRun, String adjustment, String note) {
        useCase.execute(inRun, payslipId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePayslipRequest(new BigDecimal(adjustment), note));
    }

    @Test
    void anAdjustmentChangesIncomeSoTheTaxIsRecalculated() {
        var payslip = givenHourlyPayslip(4_900_000);

        update(runId, "200000", " Thưởng lớp đông ");

        assertThat(payslip.getAdjustment()).isEqualByComparingTo("200000");
        assertThat(payslip.getNote()).isEqualTo("Thưởng lớp đông");
        assertThat(payslip.getIncomeTaxWithheld()).as("10% x 5.100.000").isEqualByComparingTo("510000");
        assertThat(payslip.getNetPay()).isEqualByComparingTo("4590000");
    }

    @Test
    void clearingTheAdjustmentDoesNotNeedANote() {
        var payslip = givenHourlyPayslip(4_900_000);

        update(runId, "0", null);

        assertThat(payslip.getAdjustment()).isEqualByComparingTo("0");
    }

    @Test
    void anAdjustmentWithoutAReasonIsRefused() {
        givenHourlyPayslip(4_900_000);

        assertThatThrownBy(() -> update(runId, "200000", "  ")).isInstanceOf(AppValidationException.class);
    }

    @Test
    void aDeductionMakingNetPayNegativeIsRefused() {
        givenHourlyPayslip(1_000_000);

        assertThatThrownBy(() -> update(runId, "-1000001", "Trừ tạm ứng")).isInstanceOf(AppValidationException.class);
    }

    @Test
    void aPayslipOfAnotherRunIsNotFound() {
        givenHourlyPayslip(1_000_000);

        assertThatThrownBy(() -> update(UUID.randomUUID(), "0", null)).isInstanceOf(PayslipNotFoundException.class);
    }

    @Test
    void refusesOnceTheRunLeftDraft() {
        givenHourlyPayslip(1_000_000);
        run.submitForApproval();

        assertThatThrownBy(() -> update(runId, "0", null)).isInstanceOf(PayrollRunNotEditableException.class);
    }
}
