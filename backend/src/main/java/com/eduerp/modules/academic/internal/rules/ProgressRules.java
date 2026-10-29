package com.eduerp.modules.academic.internal.rules;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}/{@code PayrollRules}.
 * Spec mục 5 yêu cầu đúng điều này: "Phép đếm "số buổi đã học" là một hàm THUẦN (không I/O), nhận
 * vào danh sách {@code ClassSchedule} + hai mốc thời gian, trả về số nguyên".
 *
 * <p>Mốc kết thúc được TRUYỀN VÀO chứ không đọc {@code Instant.now()} bên trong: nơi gọi quyết định
 * đó là {@code Enrollment.withdrawnAt} (đã rút - đóng băng) hay thời điểm hiện tại. Đọc đồng hồ ở
 * đây là biến hàm thành không thuần và làm Review Focus #2 không test được.
 */
public final class ProgressRules {

    /**
     * Ánh xạ tường minh thay vì dựa vào thứ tự khai báo trùng nhau của hai enum: {@code ordinal()}
     * trùng nhau hôm nay là ngẫu nhiên, thêm một giá trị vào giữa một bên là lệch toàn bộ phép đếm
     * mà không có lỗi biên dịch nào.
     */
    private static final Map<DayOfWeek, CoursesConstants.DayOfWeek> DAY_OF_WEEK_BY_JAVA_TIME = Map.of(
            DayOfWeek.MONDAY, CoursesConstants.DayOfWeek.MON,
            DayOfWeek.TUESDAY, CoursesConstants.DayOfWeek.TUE,
            DayOfWeek.WEDNESDAY, CoursesConstants.DayOfWeek.WED,
            DayOfWeek.THURSDAY, CoursesConstants.DayOfWeek.THU,
            DayOfWeek.FRIDAY, CoursesConstants.DayOfWeek.FRI,
            DayOfWeek.SATURDAY, CoursesConstants.DayOfWeek.SAT,
            DayOfWeek.SUNDAY, CoursesConstants.DayOfWeek.SUN);

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(AcademicConstants.Limits.PROGRESS_PERCENT_CAP);

    private ProgressRules() {
    }

    public static ZoneId centerZone() {
        return ZoneId.of(AcademicConstants.Schedules.CENTER_ZONE_ID);
    }

    /**
     * Số lần lịch tuần của lớp rơi vào khoảng {@code [from, to]}, tính tại GIỜ BẮT ĐẦU của buổi học
     * (spec mục 5: "đếm số lần lịch tuần của Class (ClassSchedule: dayOfWeek + startTime) rơi vào
     * khoảng [mốc_bắt_đầu, mốc_kết_thúc]"). Lịch rỗng hoặc {@code to} trước {@code from} đều cho 0,
     * không ném (Review Focus #12).
     */
    public static int countElapsedSessions(List<WeeklyScheduleSlot> schedule, Instant from, Instant to,
            ZoneId zone) {
        if (schedule.isEmpty() || to.isBefore(from)) {
            return 0;
        }
        var firstDay = LocalDate.ofInstant(from, zone);
        var lastDay = LocalDate.ofInstant(to, zone);
        return (int) firstDay.datesUntil(lastDay.plusDays(1))
                .flatMap(day -> schedule.stream()
                        .filter(slot -> slot.dayOfWeek() == DAY_OF_WEEK_BY_JAVA_TIME.get(day.getDayOfWeek()))
                        .map(slot -> day.atTime(slot.startTime()).atZone(zone).toInstant()))
                .filter(sessionStart -> !sessionStart.isBefore(from) && !sessionStart.isAfter(to))
                .count();
    }

    /**
     * Số buổi một học viên đã học, có tính vòng đời lớp (V31): đếm từ {@code max(ghi danh, khai giảng)}
     * tới {@code min(mốc của học viên, lúc lớp kết thúc)}. Lớp chưa khai giảng ({@code classStartedAt}
     * null) thì chưa có buổi nào - kể cả khi lịch tuần đã "trôi qua" vài lần từ ngày ghi danh.
     *
     * @param studentEnd mốc của riêng học viên: lúc rút (đã rút) hoặc "bây giờ"
     * @param classEndedAt lúc lớp kết thúc/huỷ, {@code null} nếu lớp chưa đóng
     */
    public static int countAttendedSessions(List<WeeklyScheduleSlot> schedule, Instant enrolledAt,
            Instant studentEnd, Instant classStartedAt, Instant classEndedAt, ZoneId zone) {
        if (classStartedAt == null) {
            return 0;
        }
        var from = enrolledAt.isAfter(classStartedAt) ? enrolledAt : classStartedAt;
        var to = classEndedAt != null && classEndedAt.isBefore(studentEnd) ? classEndedAt : studentEnd;
        return countElapsedSessions(schedule, from, to, zone);
    }

    /**
     * {@code round(min(đã học, chuẩn) / chuẩn * 100)} (spec mục 5). Trả {@code null} khi
     * {@code standardSessionCount} chưa cấu hình (null, 0 hoặc âm): "không tính được, trả null kèm
     * lý do "chưa cấu hình tổng số buổi chuẩn" — không suy đoán" (spec mục 5, Review Focus #8).
     */
    public static Integer progressPercent(int elapsedSessions, Integer standardSessionCount) {
        if (standardSessionCount == null || standardSessionCount <= 0) {
            return null;
        }
        var capped = Math.min(elapsedSessions, standardSessionCount);
        return BigDecimal.valueOf(capped)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(standardSessionCount), 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
