package com.eduerp.modules.academic.dto;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Một lớp mà học viên đang đăng nhập đã ghi danh, kèm lịch học, điểm từng cột, điểm tổng kết và
 * tiến trình (đề cương ĐATN - học viên tự tra cứu lịch học, khóa học và kết quả học tập).
 *
 * <p>{@code finalScore}/{@code progressPercent} null là trạng thái HỢP LỆ (chưa đủ cột điểm / khóa
 * chưa cấu hình tổng số buổi chuẩn) - cùng quy ước với {@link StudentClassSummaryRow}.
 * {@code classCode}/{@code courseName}/{@code teacherName} null khi bản ghi gốc đã bị xoá.
 *
 * <p>{@code classStatus}/{@code classStartDate}: lớp đang tuyển sinh thì học viên thấy "khai giảng ngày ..."
 * thay vì một lịch học chưa bắt đầu.
 */
public record MyLearningClassResponse(UUID enrollmentId, UUID classId, String classCode, UUID courseId,
        String courseName, String teacherName, EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt,
        List<WeeklyScheduleSlot> schedule, List<MyGradeItem> grades, BigDecimal finalScore,
        int elapsedSessions, Integer standardSessionCount, Integer progressPercent,
        CoursesConstants.ClassStatus classStatus, LocalDate classStartDate, LocalDate classEndDate) {
}
