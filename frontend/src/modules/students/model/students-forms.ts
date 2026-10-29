import { z } from "zod";

const optionalText = z
  .string()
  .trim()
  .transform((value) => (value.length === 0 ? null : value));

/** Tạo học viên một bước: tài khoản đăng nhập + hồ sơ. Chi nhánh để trống = do backend quyết định. */
export const createStudentFormSchema = z.object({
  email: z.string().trim().email("Email không hợp lệ"),
  fullName: z.string().trim().min(1, "Nhập họ tên học viên"),
  homeBranchId: optionalText,
  dateOfBirth: optionalText,
  phone: optionalText,
  sourceChannel: optionalText,
});

export const updateStudentFormSchema = z.object({
  dateOfBirth: optionalText,
  phone: optionalText,
  sourceChannel: optionalText,
  active: z.boolean(),
});
