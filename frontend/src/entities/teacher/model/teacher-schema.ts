import { z } from "zod";

export const teacherProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  subjects: z.array(z.string()),
  phone: z.string().nullable(),
  bio: z.string().nullable(),
  active: z.boolean(),
});

export type TeacherProfileSummary = z.infer<typeof teacherProfileSummarySchema>;

/** Tạo giáo viên MỘT bước: tài khoản đăng nhập (email mời + mật khẩu tạm) và hồ sơ giáo viên cùng lúc. */
export interface CreateTeacherPayload {
  readonly email: string;
  readonly fullName: string;
  readonly homeBranchId: string | null;
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
}

export interface UpdateTeacherProfilePayload {
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
  readonly active: boolean;
}
