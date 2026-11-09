import { PAY_TYPE } from "@/shared/constants/pay-type";
import { z } from "zod";

/** `payConfigured` chỉ cho biết đã thiết lập lương hay chưa, mức lương đi qua API riêng cần quyền lương. */
export const teacherProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  subjects: z.array(z.string()),
  phone: z.string().nullable(),
  bio: z.string().nullable(),
  active: z.boolean(),
  payConfigured: z.boolean(),
  /** Chi nhánh chủ quản: giáo viên chỉ dạy lớp thuộc chi nhánh này. */
  homeBranchId: z.string().uuid().nullable(),
  homeBranchName: z.string().nullable(),
  /** Ngày làm việc cuối, chỉ có khi đã cho nghỉ. */
  lastWorkDate: z.string().nullable(),
});

export type TeacherProfileSummary = z.infer<typeof teacherProfileSummarySchema>;

/** Một lần chuyển chi nhánh chủ quản. `fromBranchName` null khi trước đó chưa thuộc chi nhánh nào. */
export const teacherBranchChangeSchema = z.object({
  fromBranchName: z.string().nullable(),
  toBranchName: z.string().nullable(),
  changedAt: z.string(),
});

export type TeacherBranchChange = z.infer<typeof teacherBranchChangeSchema>;

export const teacherAllowanceSchema = z.object({
  name: z.string(),
  amount: z.number(),
});

export type TeacherAllowance = z.infer<typeof teacherAllowanceSchema>;

/** Điều khoản lương. `payType` null = chưa thiết lập. */
export const teacherPaySchema = z.object({
  profileId: z.string().uuid(),
  payType: z.enum([PAY_TYPE.monthly, PAY_TYPE.hourly]).nullable(),
  monthlySalary: z.number().nullable(),
  hourlyRate: z.number().nullable(),
  workStartDate: z.string().nullable(),
  probationEndDate: z.string().nullable(),
  dependentCount: z.number().int(),
  allowances: z.array(teacherAllowanceSchema),
});

export type TeacherPay = z.infer<typeof teacherPaySchema>;

export interface TeacherPayTermsPayload {
  readonly payType: (typeof PAY_TYPE)[keyof typeof PAY_TYPE];
  readonly monthlySalary: number | null;
  readonly hourlyRate: number | null;
  readonly workStartDate: string;
  readonly probationEndDate: string | null;
  readonly dependentCount: number;
  readonly allowances: readonly TeacherAllowance[];
}

/** Tạo giáo viên MỘT bước: tài khoản đăng nhập (email mời + mật khẩu tạm), hồ sơ và điều khoản lương cùng lúc. */
export interface CreateTeacherPayload {
  readonly email: string;
  readonly fullName: string;
  readonly homeBranchId: string;
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
  readonly payTerms: TeacherPayTermsPayload;
}

export interface UpdateTeacherProfilePayload {
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
}

export interface LeaveTeacherPayload {
  readonly lastWorkDate: string;
}
