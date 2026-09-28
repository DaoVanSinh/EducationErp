import { z } from "zod";

/** Độ dài tối thiểu phải khớp @Size(min = 8) ở backend, nếu không client sẽ gửi đi rồi mới bị chặn. */
const PASSWORD_MIN_LENGTH = 8;

export const loginFormSchema = z.object({
  email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
  password: z.string().min(1, "Nhập mật khẩu"),
});

export type LoginFormValues = z.infer<typeof loginFormSchema>;

export const forgotPasswordFormSchema = z.object({
  email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
});

export const resetPasswordFormSchema = z
  .object({
    token: z.string().min(1, "Liên kết đặt lại mật khẩu không hợp lệ"),
    newPassword: z.string().min(PASSWORD_MIN_LENGTH, `Mật khẩu cần tối thiểu ${PASSWORD_MIN_LENGTH} ký tự`),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Hai mật khẩu chưa giống nhau",
  });
