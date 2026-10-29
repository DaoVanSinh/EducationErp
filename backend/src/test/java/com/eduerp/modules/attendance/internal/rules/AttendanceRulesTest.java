package com.eduerp.modules.attendance.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.attendance.AttendanceConstants.AttendanceStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttendanceRulesTest {

    @Test
    void countsEachStatusAndTreatsLateAsAttended() {
        var summary = AttendanceRules.summarize(List.of(AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.LATE, AttendanceStatus.ABSENT));

        assertThat(summary.presentCount()).isEqualTo(2);
        assertThat(summary.lateCount()).isEqualTo(1);
        assertThat(summary.absentCount()).isEqualTo(1);
        assertThat(summary.excusedCount()).isZero();
        assertThat(summary.attendancePercent()).isEqualTo(75);
    }

    @Test
    void leavesExcusedAbsencesOutOfTheDenominator() {
        var summary = AttendanceRules.summarize(List.of(AttendanceStatus.PRESENT, AttendanceStatus.EXCUSED,
                AttendanceStatus.EXCUSED));

        assertThat(summary.excusedCount()).isEqualTo(2);
        assertThat(summary.attendancePercent()).isEqualTo(100);
    }

    @Test
    void reportsNullInsteadOfZeroWhenNothingCountsYet() {
        assertThat(AttendanceRules.summarize(List.of()).attendancePercent()).isNull();
        assertThat(AttendanceRules.summarize(List.of(AttendanceStatus.EXCUSED)).attendancePercent()).isNull();
    }

    @Test
    void roundsToTheNearestWholePercent() {
        var summary = AttendanceRules.summarize(List.of(AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.ABSENT));

        assertThat(summary.attendancePercent()).isEqualTo(67);
    }

    @Test
    void onlyDaysAfterTodayAreInTheFuture() {
        var today = LocalDate.of(2026, 10, 26);

        assertThat(AttendanceRules.isFutureDate(today.plusDays(1), today)).isTrue();
        assertThat(AttendanceRules.isFutureDate(today, today)).isFalse();
        assertThat(AttendanceRules.isFutureDate(today.minusDays(3), today)).isFalse();
    }
}
