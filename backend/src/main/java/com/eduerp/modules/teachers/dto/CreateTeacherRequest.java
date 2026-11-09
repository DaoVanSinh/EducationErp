package com.eduerp.modules.teachers.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Tạo giáo viên MỘT bước: tài khoản đăng nhập (email mời, mật khẩu tạm), hồ sơ và điều khoản lương trong cùng
 * một transaction, để không còn giáo viên dạy cả tháng mà không có lương. {@code homeBranchId} là chi nhánh chủ
 * quản - không giới hạn chi nhánh được dạy.
 */
public record CreateTeacherRequest(@NotBlank @Email String email, @NotBlank String fullName,
        @NotNull UUID homeBranchId, @NotEmpty List<@NotBlank String> subjects, String phone, String bio,
        @NotNull @Valid TeacherPayTermsRequest payTerms) {
}
