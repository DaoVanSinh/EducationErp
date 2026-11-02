package com.eduerp.modules.enrollment.internal.rules;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Tìm các buổi học trùng nhau giữa hai lớp. Pure, không I/O: "hôm nay" được truyền vào để test chốt được.
 *
 * <p>Hai lớp chỉ trùng khi đồng thời trùng cả ba: khoảng ngày học, thứ trong tuần và khung giờ. Vì vậy kết quả
 * là danh sách NGÀY cụ thể chứ không phải có/không: cùng "thứ 3, 18:00" có thể chỉ đụng nhau đúng một buổi khi
 * lớp này sắp kết thúc còn lớp kia vừa khai giảng.
 */
public final class ScheduleConflictRules {

    /** Ánh xạ tường minh, không dựa vào thứ tự khai báo của hai enum - cùng lý do với {@code ProgressRules}. */
    private static final Map<CoursesConstants.DayOfWeek, DayOfWeek> JAVA_DAY_OF_WEEK = Map.of(
            CoursesConstants.DayOfWeek.MON, DayOfWeek.MONDAY,
            CoursesConstants.DayOfWeek.TUE, DayOfWeek.TUESDAY,
            CoursesConstants.DayOfWeek.WED, DayOfWeek.WEDNESDAY,
            CoursesConstants.DayOfWeek.THU, DayOfWeek.THURSDAY,
            CoursesConstants.DayOfWeek.FRI, DayOfWeek.FRIDAY,
            CoursesConstants.DayOfWeek.SAT, DayOfWeek.SATURDAY,
            CoursesConstants.DayOfWeek.SUN, DayOfWeek.SUNDAY);

    private ScheduleConflictRules() {
    }

    /** Những gì cần biết về kế hoạch học của một lớp. {@code endDate} và {@code sessionCount} có thể null. */
    public record ClassPlan(LocalDate startDate, LocalDate endDate, Integer sessionCount,
            List<WeeklyScheduleSlot> schedule) {
    }

    /** Một buổi của lớp đang xét bị trùng; giờ là giờ học của lớp đang xét. */
    public record ConflictingSession(LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    /**
     * Các buổi của {@code target} trùng với {@code other}, sắp theo thời gian. Chỉ tính từ {@code today} trở đi:
     * học viên vào học muộn không thể bị trùng với những buổi đã qua.
     */
    public static List<ConflictingSession> findConflicts(ClassPlan target, ClassPlan other, LocalDate today) {
        var from = Stream.of(today, target.startDate(), other.startDate()).max(Comparator.naturalOrder()).orElseThrow();
        var until = Stream.of(plannedEnd(target), plannedEnd(other)).min(Comparator.naturalOrder()).orElseThrow();
        return target.schedule().stream()
                .flatMap(mine -> other.schedule().stream()
                        .filter(theirs -> overlaps(mine, theirs))
                        .flatMap(theirs -> datesOf(mine.dayOfWeek(), from, until))
                        .map(date -> new ConflictingSession(date, mine.startTime(), mine.endTime())))
                .distinct()
                .sorted(Comparator.comparing(ConflictingSession::date).thenComparing(ConflictingSession::startTime))
                .toList();
    }

    /** So sánh chặt: lớp này 08:00-10:00 và lớp kia 10:00-12:00 học nối tiếp được, không phải trùng lịch. */
    static boolean overlaps(WeeklyScheduleSlot first, WeeklyScheduleSlot second) {
        return first.dayOfWeek() == second.dayOfWeek()
                && first.startTime().isBefore(second.endTime())
                && second.startTime().isBefore(first.endTime());
    }

    /**
     * Ngày học cuối theo kế hoạch: ngày kết thúc đã nhập, nếu không thì ngày của buổi thứ {@code sessionCount}
     * theo lịch tuần. Thiếu cả hai thì không biết lớp dài bao lâu, nên xét một khoảng mặc định để vẫn cảnh báo.
     */
    static LocalDate plannedEnd(ClassPlan plan) {
        if (plan.endDate() != null) {
            return plan.endDate();
        }
        if (plan.sessionCount() == null || plan.sessionCount() <= 0 || plan.schedule().isEmpty()) {
            return plan.startDate().plusWeeks(EnrollmentConstants.Limits.OPEN_END_HORIZON_WEEKS);
        }
        var day = plan.startDate();
        var held = sessionsOn(day, plan.schedule());
        while (held < plan.sessionCount()) {
            day = day.plusDays(1);
            held += sessionsOn(day, plan.schedule());
        }
        return day;
    }

    private static long sessionsOn(LocalDate day, List<WeeklyScheduleSlot> schedule) {
        return schedule.stream()
                .filter(slot -> JAVA_DAY_OF_WEEK.get(slot.dayOfWeek()) == day.getDayOfWeek())
                .count();
    }

    private static Stream<LocalDate> datesOf(CoursesConstants.DayOfWeek dayOfWeek, LocalDate from, LocalDate until) {
        var first = from.with(TemporalAdjusters.nextOrSame(JAVA_DAY_OF_WEEK.get(dayOfWeek)));
        if (first.isAfter(until)) {
            return Stream.empty();
        }
        return first.datesUntil(until.plusDays(1), Period.ofWeeks(1));
    }
}
