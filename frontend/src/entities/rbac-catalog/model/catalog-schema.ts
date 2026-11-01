import { namedReferenceSchema } from "@/shared/api/schemas";
import { ACTION_LABEL, PERMISSION_SCOPE, RESOURCE_LABEL } from "@/shared/constants/permissions";
import { z } from "zod";

export const permissionOptionSchema = z.object({
  id: z.string().uuid(),
  resource: z.string(),
  action: z.string(),
});

export type PermissionOption = z.infer<typeof permissionOptionSchema>;

const permissionScopeSchema = z.enum([
  PERMISSION_SCOPE.personal,
  PERMISSION_SCOPE.branch,
  PERMISSION_SCOPE.organization,
]);

/**
 * Một vai trò kèm đúng các nhóm quyền nó đang gồm (spec mục 5). `locked` = vai trò Quản trị viên hệ thống,
 * chỉ xem - backend từ chối sửa để không ai tự khoá được hệ thống.
 */
export const roleOptionSchema = namedReferenceSchema.extend({
  code: z.string(),
  systemDefault: z.boolean(),
  locked: z.boolean(),
  permissionGroupIds: z.array(z.string().uuid()),
  accountCount: z.number().int(),
});

export type RoleOption = z.infer<typeof roleOptionSchema>;

export const permissionGroupItemSchema = z.object({
  permissionId: z.string().uuid(),
  resource: z.string(),
  action: z.string(),
  scope: permissionScopeSchema,
});

export type PermissionGroupItem = z.infer<typeof permissionGroupItemSchema>;

/** Một nhóm quyền kèm danh sách (quyền, phạm vi). `locked` = nhóm "Toàn quyền hệ thống", chỉ xem. */
export const permissionGroupOptionSchema = namedReferenceSchema.extend({
  description: z.string().nullable(),
  locked: z.boolean(),
  items: z.array(permissionGroupItemSchema),
});

export type PermissionGroupOption = z.infer<typeof permissionGroupOptionSchema>;

/** Toàn bộ dữ liệu tham chiếu của màn hình RBAC, lấy trong một lần gọi GET /api/rbac/catalog. */
export const rbacCatalogSchema = z.object({
  roles: z.array(roleOptionSchema),
  permissionGroups: z.array(permissionGroupOptionSchema),
  branches: z.array(namedReferenceSchema),
  permissions: z.array(permissionOptionSchema),
});

export type RbacCatalog = z.infer<typeof rbacCatalogSchema>;

/** Nhãn tiếng Việt cho một quyền thô: "PERMISSION_GROUP CREATE" đọc không ra nghĩa trên giao diện. */
export const permissionLabel = (permission: Pick<PermissionOption, "resource" | "action">): string => {
  const resource = RESOURCE_LABEL[permission.resource] ?? permission.resource;
  const action = ACTION_LABEL[permission.action] ?? permission.action;
  return `${action} ${resource.toLowerCase()}`;
};
