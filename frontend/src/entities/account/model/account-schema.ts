import { grantedPermissionSchema, namedReferenceSchema } from "@/shared/api/schemas";
import { z } from "zod";

/** Khớp IdentityConstants.AccountStatus ở backend. */
export const ACCOUNT_STATUS = {
  active: "ACTIVE",
  disabled: "DISABLED",
} as const;

export type AccountStatus = (typeof ACCOUNT_STATUS)[keyof typeof ACCOUNT_STATUS];

export const ACCOUNT_STATUS_LABEL: Record<AccountStatus, string> = {
  [ACCOUNT_STATUS.active]: "Đang hoạt động",
  [ACCOUNT_STATUS.disabled]: "Đã vô hiệu hoá",
};

export const accountStatusSchema = z.enum([ACCOUNT_STATUS.active, ACCOUNT_STATUS.disabled]);

/**
 * Phiên làm việc: ai đang đăng nhập và được làm gì. Trường chi nhánh và avatar có thể null - tài khoản
 * quản trị cấp tổ chức không thuộc chi nhánh nào.
 */
export const sessionSchema = z.object({
  accountId: z.string().uuid(),
  email: z.string(),
  fullName: z.string(),
  avatarUrl: z.string().nullable(),
  roleCode: z.string(),
  roleName: z.string(),
  branchId: z.string().uuid().nullable(),
  branchName: z.string().nullable(),
  permissions: z.array(grantedPermissionSchema),
});

export type Session = z.infer<typeof sessionSchema>;

export const accountSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  fullName: z.string(),
  status: accountStatusSchema,
  roleCode: z.string(),
  branchId: z.string().uuid().nullable(),
  branchName: z.string().nullable(),
  groups: z.array(namedReferenceSchema),
  lastLogin: z.string().nullable(),
});

export type AccountSummary = z.infer<typeof accountSummarySchema>;

export interface ProfileUpdatePayload {
  readonly fullName: string;
  readonly avatarUrl: string | null;
}

export interface ChangePasswordPayload {
  readonly currentPassword: string;
  readonly newPassword: string;
}

export interface CreateAccountPayload {
  readonly email: string;
  readonly fullName: string;
  readonly homeBranchId: string | null;
  readonly roleId: string;
}
