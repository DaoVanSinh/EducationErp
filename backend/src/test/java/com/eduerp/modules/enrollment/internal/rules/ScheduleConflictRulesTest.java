package com.eduerp.modules.enrollment.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.CoursesConstants.DayOfWeek;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.internal.rules.ScheduleConflictRules.ClassPlan;
import com.eduerp.modules.enrollment.internal.rules.ScheduleConflictRules.ConflictingSession;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleConflictRulesTest {

    /** 02/06/2026 là thứ Ba. */
    private static final LocalDate TUESDAY = LocalDate.of(2026, 6, 2);
    private static final LocalDate BEFORE_ALL = LocalDate.of(2026, 1, 1);

    private static WeeklyScheduleSlot slot(DayOfWeek day, int startHour, int endHour) {
        return new WeeklyScheduleSlot(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static ClassPlan plan(LocalDate start, LocalDate end, WeeklyScheduleSlot... slots) {
        return new ClassPlan(start, end, null, List.of(slots));
    }

    /** Đúng bài toán học hè: lớp này kết thúc đúng tuần lớp kia khai giảng nên chỉ đụng nhau MỘT buổi. */
    @Test
    void findsTheSingleSessionWhenTwoClassesOverlapByOneWeek() {
        var summerMath = plan(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 30), slot(DayOfWeek.TUE, 18, 20));
        var summerEnglish = plan(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 28), slot(DayOfWeek.TUE, 19, 21));

        var conflicts = ScheduleConflictRules.findConflicts(summerEnglish, summerMath, BEFORE_ALL);

        assertThat(conflicts).containsExactly(
                new ConflictingSession(LocalDate.of(2026, 6, 30), LocalTime.of(19, 0), LocalTime.of(21, 0)));
    }

    /** Cùng một khung giờ mỗi tuần thì số buổi trùng là số tuần hai lớp học chung, không phải một. */
    @Test
    void countsEveryWeekTheTwoClassesShare() {
        var first = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 18, 20));
        var second = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 18, 20));

        assertThat(ScheduleConflictRules.findConflicts(first, second, BEFORE_ALL))
                .extracting(ConflictingSession::date)
                .containsExactly(TUESDAY, TUESDAY.plusWeeks(1), TUESDAY.plusWeeks(2), TUESDAY.plusWeeks(3));
    }

    @Test
    void backToBackSessionsAreNotAConflict() {
        var morning = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 8, 10));
        var nextSession = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 10, 12));

        assertThat(ScheduleConflictRules.findConflicts(morning, nextSession, BEFORE_ALL)).isEmpty();
    }

    @Test
    void sameTimeOnADifferentDayIsNotAConflict() {
        var tuesday = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 18, 20));
        var thursday = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.THU, 18, 20));

        assertThat(ScheduleConflictRules.findConflicts(tuesday, thursday, BEFORE_ALL)).isEmpty();
    }

    /** Lớp này kết thúc trước khi lớp kia khai giảng: cùng khung giờ nhưng không bao giờ gặp nhau. */
    @Test
    void sameSlotInSeparateDateRangesIsNotAConflict() {
        var june = plan(TUESDAY, TUESDAY.plusWeeks(2), slot(DayOfWeek.TUE, 18, 20));
        var july = plan(TUESDAY.plusWeeks(3), TUESDAY.plusWeeks(5), slot(DayOfWeek.TUE, 18, 20));

        assertThat(ScheduleConflictRules.findConflicts(june, july, BEFORE_ALL)).isEmpty();
    }

    /** Học viên vào học muộn: các buổi đã qua không còn là chỗ trùng. */
    @Test
    void ignoresSessionsThatHaveAlreadyPassed() {
        var first = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 18, 20));
        var second = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 18, 20));

        assertThat(ScheduleConflictRules.findConflicts(first, second, TUESDAY.plusWeeks(3)))
                .extracting(ConflictingSession::date)
                .containsExactly(TUESDAY.plusWeeks(3));
        assertThat(ScheduleConflictRules.findConflicts(first, second, TUESDAY.plusWeeks(3).plusDays(1))).isEmpty();
    }

    /** Không có ngày kết thúc: 4 buổi, 1 buổi/tuần thì buổi cuối rơi vào tuần thứ 4 kể từ khai giảng. */
    @Test
    void estimatesTheEndFromTheSessionCountWhenThereIsNoEndDate() {
        var estimated = new ClassPlan(TUESDAY, null, 4, List.of(slot(DayOfWeek.TUE, 18, 20)));
        var longClass = plan(TUESDAY, TUESDAY.plusWeeks(20), slot(DayOfWeek.TUE, 18, 20));

        assertThat(ScheduleConflictRules.plannedEnd(estimated)).isEqualTo(TUESDAY.plusWeeks(3));
        assertThat(ScheduleConflictRules.findConflicts(estimated, longClass, BEFORE_ALL)).hasSize(4);
    }

    /** Không biết lớp dài bao lâu thì vẫn phải cảnh báo, không được coi như không trùng. */
    @Test
    void stillWarnsWhenAClassHasNeitherEndDateNorSessionCount() {
        var unknownLength = new ClassPlan(TUESDAY, null, null, List.of(slot(DayOfWeek.TUE, 18, 20)));
        var other = plan(TUESDAY, TUESDAY.plusWeeks(1), slot(DayOfWeek.TUE, 18, 20));

        assertThat(ScheduleConflictRules.findConflicts(unknownLength, other, BEFORE_ALL)).hasSize(2);
    }

    /** Hai khung của lớp kia cùng đè lên một khung của lớp này vẫn chỉ là một buổi bị trùng. */
    @Test
    void reportsOneSessionWhenTwoOtherSlotsOverlapTheSameSlot() {
        var target = plan(TUESDAY, TUESDAY, slot(DayOfWeek.TUE, 18, 21));
        var other = plan(TUESDAY, TUESDAY, slot(DayOfWeek.TUE, 17, 19), slot(DayOfWeek.TUE, 20, 22));

        assertThat(ScheduleConflictRules.findConflicts(target, other, BEFORE_ALL)).hasSize(1);
    }

    @Test
    void classWithoutAScheduleConflictsWithNothing() {
        var empty = plan(TUESDAY, TUESDAY.plusWeeks(3));
        var other = plan(TUESDAY, TUESDAY.plusWeeks(3), slot(DayOfWeek.TUE, 18, 20));

        assertThat(ScheduleConflictRules.findConflicts(empty, other, BEFORE_ALL)).isEmpty();
    }
}
