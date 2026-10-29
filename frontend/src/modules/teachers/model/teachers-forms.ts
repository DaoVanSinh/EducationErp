import { z } from "zod";

const optionalText = z
  .string()
  .trim()
  .transform((value) => (value.length === 0 ? null : value));

const subjectList = z
  .string()
  .trim()
  .transform((value) =>
    value.length === 0
      ? []
      : value
          .split(",")
          .map((subject) => subject.trim())
          .filter((subject) => subject.length > 0),
  );

/** Tạo giáo viên một bước: tài khoản đăng nhập + hồ sơ giáo viên. */
export const createTeacherFormSchema = z.object({
  email: z.string().trim().email("Email không hợp lệ"),
  fullName: z.string().trim().min(1, "Nhập họ tên giáo viên"),
  homeBranchId: optionalText,
  subjects: subjectList,
  phone: optionalText,
  bio: optionalText,
});

export const updateTeacherFormSchema = z.object({
  subjects: subjectList,
  phone: optionalText,
  bio: optionalText,
  active: z.boolean(),
});
