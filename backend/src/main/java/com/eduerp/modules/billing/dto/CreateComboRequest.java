package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Kế toán chọn sẵn các ghi danh ĐANG ACTIVE của một học viên; hệ thống tự tính % giảm theo số khoá.
 * Hạn đóng nhập tay, KHÔNG suy ra từ lịch học (spec mục 1 + 12).
 *
 * <p>{@code @NotEmpty} chỉ chặn danh sách rỗng - mốc "≥ 2 phần tử KHÁC NHAU" là quy tắc nghiệp vụ
 * (phải loại trùng trước khi đếm), nằm ở {@code CreateCombo} dưới dạng
 * {@code MinimumComboSizeException}, không nhét được vào một annotation.
 */
public record CreateComboRequest(@NotNull UUID studentProfileId,
        @NotEmpty List<@NotNull UUID> enrollmentIds, @NotNull LocalDate dueDate) {
}
