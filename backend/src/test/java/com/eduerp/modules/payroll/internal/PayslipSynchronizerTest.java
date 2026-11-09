package com.eduerp.modules.payroll.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.attendance.AttendanceManagement;
import com.eduerp.modules.attendance.AttendanceManagement.TeachingSummary;
import com.eduerp.modules.payroll.PayslipFixtures;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.teachers.TeachersConstants.PayType;
import com.eduerp.modules.teachers.TeachersManagement;
import com.eduerp.modules.teachers.TeachersManagement.PayTerms;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayslipSynchronizerTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);

    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final TeachersManagement teachers = mock(TeachersManagement.class);
    private final AttendanceManagement attendance = mock(AttendanceManagement.class);
    private final com.eduerp.modules.identity.IdentityManagement identity =
            mock(com.eduerp.modules.identity.IdentityManagement.class);
    private final PayslipSynchronizer synchronizer = new PayslipSynchronizer(payslips, teachers, attendance,
            identity);

    private final PayrollRun run = new PayrollRun(2026, 10);
    private final UUID hourlyTeacher = UUID.randomUUID();
    private final UUID monthlyTeacher = UUID.randomUUID();

    private static PayTerms hourly(UUID accountId, long rate) {
        return new PayTerms(accountId, PayType.HOURLY, null, BigDecimal.valueOf(rate), LONG_AGO, null, null, 0,
                BigDecimal.ZERO);
    }

    private static PayTerms monthly(UUID accountId, long salary, LocalDate lastWorkDate) {
        return new PayTerms(accountId, PayType.MONTHLY, BigDecimal.valueOf(salary), null, LONG_AGO, null, lastWorkDate,
                0, BigDecimal.ZERO);
    }

    private void givenTaught(TeachingSummary... summaries) {
        var map = new java.util.HashMap<UUID, TeachingSummary>();
        for (var summary : summaries) {
            map.put(summary.teacherAccountId(), summary);
        }
        when(attendance.teachingSummaries(OCTOBER.atDay(1), OCTOBER.atEndOfMonth())).thenReturn(map);
    }

    @BeforeEach
    void saveReturnsItsArgument() {
        when(payslips.saveAll(anyCollection())).thenAnswer(invocation -> List.copyOf(
                invocation.<java.util.Collection<Object>>getArgument(0)));
        when(payslips.findAllByPayrollRun_Id(any())).thenReturn(List.of());
    }

    @Test
    void hoursComeFromAttendance() {
        when(teachers.listPayTerms()).thenReturn(List.of(hourly(hourlyTeacher, 200_000)));
        givenTaught(new TeachingSummary(hourlyTeacher, 12, 18 * 60));

        var created = synchronizer.synchronize(run);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getSessionCount()).isEqualTo(12);
        assertThat(created.get(0).getHoursWorked()).isEqualByComparingTo("18");
        assertThat(created.get(0).getGrossPay()).isEqualByComparingTo("3600000");
    }

    @Test
    void anHourlyTeacherWhoDidNotTeachGetsNoPayslipButAMonthlyOneStillDoes() {
        when(teachers.listPayTerms()).thenReturn(List.of(hourly(hourlyTeacher, 200_000),
                monthly(monthlyTeacher, 10_000_000, null)));
        givenTaught();

        var created = synchronizer.synchronize(run);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getAccountId()).isEqualTo(monthlyTeacher);
    }

    @Test
    void aTeacherWhoLeftStillGetsPaidForTheSessionsTaught() {
        var left = new PayTerms(hourlyTeacher, PayType.HOURLY, null, BigDecimal.valueOf(200_000), LONG_AGO, null,
                LocalDate.of(2026, 10, 10), 0, BigDecimal.ZERO);
        when(teachers.listPayTerms()).thenReturn(List.of(left));
        givenTaught(new TeachingSummary(hourlyTeacher, 2, 180));

        assertThat(synchronizer.synchronize(run)).hasSize(1);
    }

    @Test
    void recalculatingKeepsTheAdjustmentAndNote() {
        var existing = PayslipFixtures.hourly(run, hourlyTeacher, 1_000_000, 5);
        existing.changeAdjustment(new BigDecimal("300000"), "Thưởng");
        when(payslips.findAllByPayrollRun_Id(any())).thenReturn(List.of(existing));
        when(teachers.listPayTerms()).thenReturn(List.of(hourly(hourlyTeacher, 200_000)));
        givenTaught(new TeachingSummary(hourlyTeacher, 12, 25 * 60));

        var updated = synchronizer.synchronize(run);

        assertThat(updated).containsExactly(existing);
        assertThat(existing.getGrossPay()).isEqualByComparingTo("5000000");
        assertThat(existing.getAdjustment()).isEqualByComparingTo("300000");
        assertThat(existing.getNote()).isEqualTo("Thưởng");
        assertThat(existing.getIncomeTaxWithheld()).as("10% x 5.300.000").isEqualByComparingTo("530000");
    }

    @Test
    void aPayslipOfSomeoneWithNothingLeftToPayIsRemoved() {
        var stale = PayslipFixtures.hourly(run, hourlyTeacher, 1_000_000, 5);
        when(payslips.findAllByPayrollRun_Id(any())).thenReturn(List.of(stale));
        when(teachers.listPayTerms()).thenReturn(List.of(hourly(hourlyTeacher, 200_000)));
        givenTaught();

        assertThat(synchronizer.synchronize(run)).isEmpty();
        verify(payslips).deleteAll(org.mockito.ArgumentMatchers.argThat(deleted -> {
            var items = new java.util.ArrayList<>();
            deleted.forEach(items::add);
            return items.contains(stale);
        }));
    }

    @Test
    void missingPayTermsListsTeachersWithoutTermsWithTheirSessions() {
        var noTermsIdle = UUID.randomUUID();
        var noTermsTaught = UUID.randomUUID();
        when(teachers.listPayTerms()).thenReturn(List.of(hourly(hourlyTeacher, 200_000)));
        when(teachers.listActiveAccountsWithoutPayTerms()).thenReturn(List.of(noTermsIdle, noTermsTaught));
        givenTaught(new TeachingSummary(noTermsTaught, 4, 360), new TeachingSummary(hourlyTeacher, 2, 180));

        assertThat(synchronizer.missingPayTerms(OCTOBER)).isEqualTo(Map.of(noTermsIdle, 0, noTermsTaught, 4));
    }
}
