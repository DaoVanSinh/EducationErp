package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Kế toán chọn các ghi danh ĐANG HỌC của nhiều học viên cùng một khoá; hệ thống tự tính % giảm theo số người.
 * Mốc "ít nhất 2 người khác nhau" là quy tắc nghiệp vụ (phải loại trùng trước khi đếm), nằm ở use case.
 */
public record CreateGroupRegistrationRequest(@NotEmpty List<@NotNull UUID> enrollmentIds) {
}
