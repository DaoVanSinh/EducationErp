package com.eduerp.modules.students.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tạo học viên MỘT bước: tài khoản đăng nhập (email mời, mật khẩu tạm) + hồ sơ học viên trong cùng một
 * transaction. {@code homeBranchId} bị bỏ qua với người chỉ có quyền theo chi nhánh - họ luôn tạo học
 * viên cho chi nhánh của chính mình.
 */
public record CreateStudentRequest(@NotBlank @Email String email, @NotBlank String fullName, UUID homeBranchId,
        LocalDate dateOfBirth, String phone, String sourceChannel) {
}
