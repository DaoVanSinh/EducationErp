package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollPeriodNotEndedException;
import com.eduerp.modules.payroll.PayrollRunEmptyException;
import com.eduerp.modules.payroll.PayrollRunMissingPayTermsException;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.internal.PayrollClock;
import com.eduerp.modules.payroll.internal.PayslipSynchronizer;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitPayrollRunForApprovalTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipSynchronizer synchronizer = mock(PayslipSynchronizer.class);
    private final PayrollClock clock = new PayrollClock(
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private final SubmitPayrollRunForApproval useCase = new SubmitPayrollRunForApproval(runs, synchronizer, clock);

    private final UUID runId = UUID.randomUUID();

    private PayrollRun givenRun(int year, int month) {
        var run = new PayrollRun(year, month);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        return run;
    }

    private void submit() {
        useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void recalculatesThenMovesToPendingApproval() {
        var run = givenRun(2026, 10);
        when(synchronizer.missingPayTerms(OCTOBER)).thenReturn(Map.of());
        when(synchronizer.synchronize(run)).thenReturn(List.of(PayslipFixtures.monthly(run, UUID.randomUUID(), 10_000_000)));

        submit();

        verify(synchronizer).synchronize(run);
        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.PENDING_APPROVAL);
    }

    @Test
    void resubmittingClearsTheOldRejectionReason() {
        var run = givenRun(2026, 10);
        run.submitForApproval();
        run.reject("Sai số giờ");
        when(synchronizer.missingPayTerms(OCTOBER)).thenReturn(Map.of());
        when(synchronizer.synchronize(run)).thenReturn(List.of(PayslipFixtures.monthly(run, UUID.randomUUID(), 10_000_000)));

        submit();

        assertThat(run.getRejectionReason()).isNull();
    }

    @Test
    void refusesWhileThePeriodIsStillRunning() {
        givenRun(2026, 11);

        assertThatThrownBy(this::submit).isInstanceOf(PayrollPeriodNotEndedException.class);
    }

    @Test
    void refusesWhenATeacherTaughtButHasNoPayTerms() {
        var run = givenRun(2026, 10);
        when(synchronizer.missingPayTerms(OCTOBER)).thenReturn(Map.of(UUID.randomUUID(), 8));

        assertThatThrownBy(this::submit).isInstanceOf(PayrollRunMissingPayTermsException.class);
        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.DRAFT);
    }

    @Test
    void aTeacherWithoutPayTermsWhoDidNotTeachDoesNotBlock() {
        var run = givenRun(2026, 10);
        when(synchronizer.missingPayTerms(OCTOBER)).thenReturn(Map.of(UUID.randomUUID(), 0));
        when(synchronizer.synchronize(run)).thenReturn(List.of(PayslipFixtures.monthly(run, UUID.randomUUID(), 10_000_000)));

        submit();

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.PENDING_APPROVAL);
    }

    @Test
    void refusesAnEmptyRun() {
        var run = givenRun(2026, 10);
        when(synchronizer.missingPayTerms(OCTOBER)).thenReturn(Map.of());
        when(synchronizer.synchronize(run)).thenReturn(List.of());

        assertThatThrownBy(this::submit).isInstanceOf(PayrollRunEmptyException.class);
    }

    @Test
    void refusesARunThatIsNoLongerDraft() {
        givenRun(2026, 10).submitForApproval();

        assertThatThrownBy(this::submit).isInstanceOf(PayrollRunNotEditableException.class);
    }
}
