import { z } from "zod";

export const studentProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  dateOfBirth: z.string().nullable(),
  phone: z.string().nullable(),
  sourceChannel: z.string().nullable(),
  active: z.boolean(),
});

export type StudentProfileSummary = z.infer<typeof studentProfileSummarySchema>;

/**
 * Tạo học viên MỘT bước: tài khoản đăng nhập (email mời + mật khẩu tạm) và hồ sơ học viên cùng lúc.
 * {@code homeBranchId} bị backend bỏ qua với người chỉ có quyền theo chi nhánh (luôn là chi nhánh của họ).
 */
export interface CreateStudentPayload {
  readonly email: string;
  readonly fullName: string;
  readonly homeBranchId: string | null;
  readonly dateOfBirth: string | null;
  readonly phone: string | null;
  readonly sourceChannel: string | null;
}

export interface UpdateStudentProfilePayload {
  readonly dateOfBirth: string | null;
  readonly phone: string | null;
  readonly sourceChannel: string | null;
  readonly active: boolean;
}
