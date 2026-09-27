import { namedReferenceSchema } from "@/shared/api/schemas";
import { ACTION_LABEL, RESOURCE_LABEL } from "@/shared/constants/permissions";
import { z } from "zod";

export const permissionOptionSchema = z.object({
  id: z.string().uuid(),
  resource: z.string(),
  action: z.string(),
});

export type PermissionOption = z.infer<typeof permissionOptionSchema>;

/** Toàn bộ dữ liệu tham chiếu của màn hình RBAC, lấy trong một lần gọi GET /api/rbac/catalog. */
export const rbacCatalogSchema = z.object({
  roles: z.array(namedReferenceSchema),
  groups: z.array(namedReferenceSchema),
  permissionGroups: z.array(namedReferenceSchema),
  branches: z.array(namedReferenceSchema),
  permissions: z.array(permissionOptionSchema),
});

export type RbacCatalog = z.infer<typeof rbacCatalogSchema>;

/** Nhãn tiếng Việt cho một quyền thô: "PERMISSION_GROUP CREATE" đọc không ra nghĩa trên giao diện. */
export const permissionLabel = (permission: PermissionOption): string => {
  const resource = RESOURCE_LABEL[permission.resource] ?? permission.resource;
  const action = ACTION_LABEL[permission.action] ?? permission.action;
  return `${action} ${resource.toLowerCase()}`;
};
