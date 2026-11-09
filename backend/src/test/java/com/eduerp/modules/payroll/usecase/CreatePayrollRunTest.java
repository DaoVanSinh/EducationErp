package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollPeriodInFutureException;
import com.eduerp.modules.payroll.PayrollRunAlreadyExistsException;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.internal.PayrollClock;
import com.eduerp.modules.payroll.internal.PayslipSynchronizer;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreatePayrollRunTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipSynchronizer synchronizer = mock(PayslipSynchronizer.class);
    private final PayrollClock clock = new PayrollClock(
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    private final CreatePayrollRun useCase = new CreatePayrollRun(runs, synchronizer, clock);

    @BeforeEach
    void saveReturnsTheRun() {
        when(runs.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void create(int year, int month) {
        useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(year, month));
    }

    @Test
    void createsADraftRunWithThePeriodDatesAndTotalsOfItsPayslips() {
        var run = new PayrollRun(2026, 10);
        when(runs.save(any(PayrollRun.class))).thenReturn(run);
        when(synchronizer.synchronize(run)).thenReturn(List.of(PayslipFixtures.monthly(run, UUID.randomUUID(), 10_000_000)));

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 10));

        assertThat(response.status()).isEqualTo(PayrollConstants.PayrollRunStatus.DRAFT);
        assertThat(response.periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(response.payslipCount()).isEqualTo(1);
        assertThat(response.totalGrossPay()).isEqualByComparingTo("10000000");
        assertThat(response.totalNetPay()).isEqualByComparingTo("8950000");
        assertThat(response.totalEmployerInsurance()).isEqualByComparingTo("2150000");
    }

    @Test
    void theCurrentMonthCanBeCreatedToPreview() {
        create(2026, 11);

        verify(runs).save(any(PayrollRun.class));
    }

    @Test
    void refusesAMonthThatHasNotStarted() {
        assertThatThrownBy(() -> create(2026, 12)).isInstanceOf(PayrollPeriodInFutureException.class);
        verify(runs, never()).save(any());
    }

    @Test
    void refusesADuplicatePeriod() {
        when(runs.existsByYearAndMonth(2026, 10)).thenReturn(true);

        assertThatThrownBy(() -> create(2026, 10)).isInstanceOf(PayrollRunAlreadyExistsException.class);
        verify(runs, never()).save(any());
    }
}
