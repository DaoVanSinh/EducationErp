package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng trong cấu hình cột điểm của khóa học khi tạo/sửa - mirror {@link WeeklyScheduleSlot} ở
 * chỗ PATCH gửi lại TOÀN BỘ danh sách, nhưng KHÁC ở một điểm bắt buộc: {@code id} có mặt (nullable)
 * vì {@code course_grade_columns} bị {@code grade_entries} tham chiếu bằng khoá ngoại (V24) -
 * {@code ClassSchedule} không bị ai tham chiếu nên xoá-và-tạo-lại vô hại, còn ở đây xoá một cột đã
 * có điểm là vỡ FK (Final review Critical #1). {@code id} null = cột MỚI; {@code id} khớp một cột
 * đang có = SỬA TẠI CHỖ, giữ nguyên khoá chính nên điểm đã nhập không bị mồ côi.
 * {@code maxScore} phải dương vì nó là số bị chia trong công thức điểm tổng kết (spec mục 6).
 */
public record GradeColumnSlot(UUID id, @NotBlank String name, @NotNull @Positive BigDecimal weightPercent,
        @NotNull @Positive BigDecimal maxScore, @PositiveOrZero int displayOrder) {
}
