import { z } from "zod";

/** Khớp @Size(min = 8) của ChangePasswordRequest ở backend. */
const PASSWORD_MIN_LENGTH = 8;

export const profileFormSchema = z.object({
  fullName: z.string().min(1, "Nhập họ tên"),
  /**
   * Ô để trống nghĩa là "xoá ảnh đại diện": backend nhận avatarUrl = null, nên chuỗi rỗng phải được
   * đổi thành null ở đây thay vì gửi "" rồi lưu một URL rỗng vào cơ sở dữ liệu.
   */
  avatarUrl: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value))
    .refine((value) => value === null || z.string().url().safeParse(value).success, {
      message: "Địa chỉ ảnh phải là một URL hợp lệ",
    }),
});

export const changePasswordFormSchema = z
  .object({
    currentPassword: z.string().min(1, "Nhập mật khẩu hiện tại"),
    newPassword: z.string().min(PASSWORD_MIN_LENGTH, `Mật khẩu cần tối thiểu ${PASSWORD_MIN_LENGTH} ký tự`),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Hai mật khẩu chưa giống nhau",
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ["newPassword"],
    message: "Mật khẩu mới phải khác mật khẩu hiện tại",
  });
