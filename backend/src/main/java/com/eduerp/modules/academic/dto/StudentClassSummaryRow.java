package com.eduerp.modules.academic.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một lớp trong bảng "Kết quả học tập" của trang chi tiết học viên. {@code classCode}/
 * {@code courseName} có thể null nếu lớp đã bị xoá hẳn khỏi {@code modules.courses} - hiển thị
 * "(đã xoá)" thay vì vỡ trang, không bao giờ in "null" (cùng mandate với {@code ChatPromptRules} của
 * các phase trước).
 *
 * <p>{@code finalScore}/{@code progressPercent} null là trạng thái HỢP LỆ (chưa đủ cột điểm đã
 * chấm / khóa chưa cấu hình tổng số buổi chuẩn) - cùng triết lý với {@code StudentGradesRow}/
 * {@code StudentProgressRow} của bảng điểm một lớp.
 */
public record StudentClassSummaryRow(UUID classId, String classCode, String courseName,
        EnrollmentConstants.EnrollmentStatus status, BigDecimal finalScore, Integer progressPercent) {
}
