package com.eduerp.modules.academic.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hàm thuần, không I/O, không DB (spec mục 5). Mọi mốc thời gian trong test được viết theo giờ địa
 * phương của trung tâm rồi quy về {@code Instant}, đúng cách usecase sẽ làm.
 */
class ProgressRulesTest {

    private static final ZoneId ZONE = ProgressRules.centerZone();

    /** Lịch mẫu: Thứ Hai và Thứ Tư, 18:00-20:00 - hai buổi mỗi tuần. */
    private static final List<WeeklyScheduleSlot> MON_WED_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)),
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    /** 2026-01-05 là Thứ Hai; tuần đó có buổi 05/01 (T2) và 07/01 (T4). */
    @Test
    void countsOneSessionPerScheduledSlotThatHasAlreadyStarted() {
        var count = ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), at(2026, 1, 7, 23, 0), ZONE);

        assertThat(count).isEqualTo(2);
    }

    /** Buổi học được tính tại GIỜ BẮT ĐẦU: 18:00 cùng ngày nhưng mốc kết thúc là 17:59 thì buổi đó
     * chưa diễn ra, không được tính. */
    @Test
    void doesNotCountASessionThatHasNotStartedYet() {
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), at(2026, 1, 5, 17, 59), ZONE)).isZero();
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), at(2026, 1, 5, 18, 0), ZONE))
                .as("đúng 18:00 là đã bắt đầu - tính")
                .isEqualTo(1);
    }

    /**
     * Review Focus #1: hai học viên cùng lớp, {@code enrolledAt} khác nhau phải ra số buổi khác
     * nhau. Học viên A vào từ 05/01, học viên B vào giữa chừng 14/01 (Thứ Tư) - cùng mốc kết thúc
     * 21/01 (Thứ Tư) thì A học 7 buổi (05,07,12,14,19,21 → 6; xem khẳng định bên dưới) và B ít hơn.
     */
    @Test
    void countsOnlyTheSessionsAfterAMidCourseEnrolment() {
        var end = at(2026, 1, 21, 23, 0);

        var fromTheStart = ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), end, ZONE);
        var fromMidCourse = ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 14, 8, 0), end, ZONE);

        assertThat(fromTheStart)
                .as("05,07,12,14,19,21 tháng 1 = 6 buổi")
                .isEqualTo(6);
        assertThat(fromMidCourse)
                .as("14,19,21 tháng 1 = 3 buổi")
                .isEqualTo(3);
        assertThat(fromMidCourse).isLessThan(fromTheStart);
    }

    /**
     * Review Focus #2: mốc kết thúc là {@code withdrawnAt}, nên gọi lại sau bao lâu cũng ra đúng con
     * số đã đóng băng. Hai lần gọi dưới đây chỉ khác nhau ở chỗ lần thứ hai "được gọi muộn hơn" -
     * nhưng vì {@code to} vẫn là mốc rút, kết quả phải y nguyên.
     */
    @Test
    void freezesAtWithdrawalEvenWhenCalledMuchLater() {
        var enrolledAt = at(2026, 1, 5, 8, 0);
        var withdrawnAt = at(2026, 1, 13, 9, 0);

        var atWithdrawal = ProgressRules.countElapsedSessions(MON_WED_EVENING, enrolledAt, withdrawnAt, ZONE);
        var threeMonthsLater = ProgressRules.countElapsedSessions(MON_WED_EVENING, enrolledAt, withdrawnAt, ZONE);

        assertThat(atWithdrawal).as("05,07,12 tháng 1 = 3 buổi").isEqualTo(3);
        assertThat(threeMonthsLater).isEqualTo(atWithdrawal);
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING, enrolledAt,
                at(2026, 4, 13, 9, 0), ZONE))
                .as("nếu nơi gọi truyền sai mốc (dùng now thay vì withdrawnAt) thì số phải tăng - "
                        + "khẳng định này chốt rằng chính mốc to là thứ đóng băng tiến trình")
                .isGreaterThan(atWithdrawal);
    }

    /** Review Focus #12: lớp chưa có lịch học nào (trạng thái hợp lệ, xem
     * {@code ClassAdminControllerIT.acceptsAnEmptyScheduleOnCreate}) → 0 buổi, không ném. */
    @Test
    void countsZeroSessionsForAClassWithoutAnySchedule() {
        assertThat(ProgressRules.countElapsedSessions(List.of(),
                at(2026, 1, 5, 8, 0), at(2026, 6, 5, 8, 0), ZONE)).isZero();
    }

    /** Mốc kết thúc trước mốc bắt đầu (dữ liệu lệch, đồng hồ nhảy) → 0, không ra số âm. */
    @Test
    void countsZeroWhenTheEndMarkIsBeforeTheStartMark() {
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 20, 8, 0), at(2026, 1, 5, 8, 0), ZONE)).isZero();
    }

    /** Review Focus #8: chưa cấu hình tổng số buổi chuẩn → null, KHÔNG chia cho 0. */
    @Test
    void returnsNullWhenTheStandardSessionCountIsNotConfigured() {
        assertThat(ProgressRules.progressPercent(6, null)).isNull();
    }

    /** Số buổi chuẩn 0 hoặc âm là dữ liệu vô nghĩa - cũng là "chưa cấu hình", vẫn không chia cho 0. */
    @Test
    void returnsNullWhenTheStandardSessionCountIsZeroOrNegative() {
        assertThat(ProgressRules.progressPercent(6, 0)).isNull();
        assertThat(ProgressRules.progressPercent(6, -4)).isNull();
    }

    @Test
    void roundsThePercentToTheNearestWholeNumber() {
        assertThat(ProgressRules.progressPercent(6, 24)).isEqualTo(25);
        assertThat(ProgressRules.progressPercent(1, 3))
                .as("1/3 = 33.33 → 33")
                .isEqualTo(33);
        assertThat(ProgressRules.progressPercent(2, 3))
                .as("2/3 = 66.67 → 67")
                .isEqualTo(67);
    }

    /** Ranh giới trần của spec mục 5: {@code min(đã học, chuẩn)} nên học bù vượt số buổi chuẩn vẫn
     * dừng đúng ở 100, không ra 112. */
    @Test
    void capsThePercentAtOneHundred() {
        assertThat(ProgressRules.progressPercent(24, 24)).isEqualTo(100);
        assertThat(ProgressRules.progressPercent(27, 24)).isEqualTo(100);
    }

    @Test
    void reportsZeroPercentBeforeTheFirstSession() {
        assertThat(ProgressRules.progressPercent(0, 24)).isZero();
    }

    /** Múi giờ là quyết định tường minh của module, không phải múi giờ của máy chạy server - cùng dữ
     * liệu phải cho cùng con số trên mọi môi trường. */
    @Test
    void centerZoneIsAnExplicitConfiguredZone() {
        assertThat(ProgressRules.centerZone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
