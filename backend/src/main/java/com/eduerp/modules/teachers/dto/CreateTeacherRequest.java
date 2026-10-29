package com.eduerp.modules.teachers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Tạo giáo viên MỘT bước: tài khoản đăng nhập (email mời, mật khẩu tạm) + hồ sơ giáo viên trong cùng một
 * transaction. {@code homeBranchId} là chi nhánh chủ quản - không giới hạn chi nhánh được dạy (spec mục 4).
 */
public record CreateTeacherRequest(@NotBlank @Email String email, @NotBlank String fullName, UUID homeBranchId,
        @NotNull List<String> subjects, String phone, String bio) {
}
