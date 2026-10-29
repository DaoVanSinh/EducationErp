package com.eduerp.modules.attendance.internal.rules;

import com.eduerp.modules.attendance.AttendanceConstants;
import com.eduerp.modules.attendance.dto.AttendanceSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;

/** Quyết định thuần của điểm danh - không I/O. */
public final class AttendanceRules {

    private AttendanceRules() {
    }

    /**
     * Đếm theo trạng thái và tính tỷ lệ chuyên cần = (có mặt + đi muộn) / (có mặt + đi muộn + vắng),
     * làm tròn tới số nguyên. Vắng CÓ PHÉP không tính vào mẫu số: học viên báo nghỉ hợp lệ không bị kéo
     * tỷ lệ xuống. Mẫu số 0 (chưa có buổi nào tính được) trả {@code null} - không phải 0%.
     */
    public static AttendanceSummary summarize(Collection<AttendanceConstants.AttendanceStatus> statuses) {
        var present = count(statuses, AttendanceConstants.AttendanceStatus.PRESENT);
        var late = count(statuses, AttendanceConstants.AttendanceStatus.LATE);
        var absent = count(statuses, AttendanceConstants.AttendanceStatus.ABSENT);
        var excused = count(statuses, AttendanceConstants.AttendanceStatus.EXCUSED);
        return new AttendanceSummary(present, late, absent, excused,
                percent(present + late, present + late + absent));
    }

    /** Không điểm danh trước cho một ngày chưa tới - "hôm nay" tính theo giờ trung tâm. */
    public static boolean isFutureDate(LocalDate sessionDate, LocalDate today) {
        return sessionDate.isAfter(today);
    }

    private static int count(Collection<AttendanceConstants.AttendanceStatus> statuses,
            AttendanceConstants.AttendanceStatus wanted) {
        return (int) statuses.stream().filter(status -> status == wanted).count();
    }

    private static Integer percent(int attended, int counted) {
        if (counted == 0) {
            return null;
        }
        return BigDecimal.valueOf((long) attended * AttendanceConstants.Limits.PERCENT_SCALE)
                .divide(BigDecimal.valueOf(counted), 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
