import { z } from "zod";

/** Khớp @Size(min = 8) của ChangePasswordRequest ở backend. */
const PASSWORD_MIN_LENGTH = 8;

/**
 * Hằng số chuẩn hoá tải ảnh đại diện lên MinIO — theo khuôn {@code LOGO} của mcp-hub.
 */
export const PROFILE_AVATAR = {
  LABEL: "Ảnh đại diện",
  PICK: "Chọn ảnh từ máy",
  CHANGE: "Đổi ảnh khác",
  REMOVE: "Dùng chữ cái đầu",
  HINT: "Ảnh PNG, JPEG hoặc WebP tối đa 5 MB. Ảnh được chuẩn hoá và lưu trữ trên MinIO.",
  INVALID: "Chọn ảnh PNG, JPEG hoặc WebP tối đa 5 MB.",
  ACCEPT: "image/png,image/jpeg,image/webp",
  TYPES: ["image/png", "image/jpeg", "image/webp"] as readonly string[],
  MAX_BYTES: 5 * 1024 * 1024,
} as const;

export const profileFormSchema = z.object({
  fullName: z.string().min(1, "Nhập họ tên"),
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
