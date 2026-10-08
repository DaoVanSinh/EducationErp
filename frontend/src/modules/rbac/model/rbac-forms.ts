import { PERMISSION_SCOPE } from "@/shared/constants/permissions";
import { z } from "zod";

export const permissionScopeSchema = z.enum([
  PERMISSION_SCOPE.personal,
  PERMISSION_SCOPE.branch,
  PERMISSION_SCOPE.organization,
]);

/** Mã vai trò đi vào cấu hình và câu lệnh phân quyền, nên giới hạn ký tự ngay từ ô nhập. */
const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]*$/;

export const createRoleFormSchema = z.object({
  code: z
    .string()
    .min(1, "Nhập mã vai trò")
    .regex(ROLE_CODE_PATTERN, "Mã chỉ gồm chữ in hoa, số và dấu gạch dưới, ví dụ BRANCH_MANAGER"),
  name: z.string().min(1, "Nhập tên vai trò"),
  permissionGroupIds: z.array(z.string().uuid()),
});

export const createPermissionGroupFormSchema = z.object({
  name: z.string().min(1, "Nhập tên nhóm quyền"),
  description: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  items: z
    .array(z.object({ permissionId: z.string().uuid(), scope: permissionScopeSchema }))
    .min(1, "Chọn ít nhất một quyền cho nhóm"),
});

export const assignGroupFormSchema = z.object({
  groupId: z.string().uuid("Chọn một nhóm người dùng"),
});

export const transferBranchFormSchema = z.object({
  branchId: z.string().uuid("Chọn một chi nhánh"),
});

export const createAccountFormSchema = z.object({
  email: z.string().min(1, "Nhập email").email("Email không đúng định dạng"),
  fullName: z.string().min(1, "Nhập họ tên"),
  homeBranchId: z.string(),
  roleId: z.string().uuid("Chọn vai trò"),
});
