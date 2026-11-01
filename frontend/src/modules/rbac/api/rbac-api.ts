import { createdIdSchema } from "@/modules/rbac/model/rbac-responses";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";
import type { PermissionScope } from "@/shared/constants/permissions";

export interface CreateRolePayload {
  readonly code: string;
  readonly name: string;
  readonly permissionGroupIds: readonly string[];
}

/** Mã vai trò bất biến - chỉ đổi tên và bộ nhóm quyền. */
export interface UpdateRolePayload {
  readonly name: string;
  readonly permissionGroupIds: readonly string[];
}

export interface CreatePermissionGroupPayload {
  readonly name: string;
  readonly description: string | null;
  readonly items: readonly { readonly permissionId: string; readonly scope: PermissionScope }[];
}

/** Các thao tác quản trị RBAC. Endpoint tạo trả về id của bản ghi vừa tạo. */
export const rbacApi = {
  async createRole(payload: CreateRolePayload): Promise<string> {
    return createdIdSchema.parse(await apiClient.post<unknown>(API_ROUTE.rbac.roles, payload));
  },

  async createPermissionGroup(payload: CreatePermissionGroupPayload): Promise<string> {
    return createdIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.rbac.permissionGroups, payload),
    );
  },

  async updateRole(roleId: string, payload: UpdateRolePayload): Promise<void> {
    await apiClient.put<void>(API_ROUTE.rbac.role(roleId), payload);
  },

  /** Cùng hình dạng với tạo mới - gửi lại TOÀN BỘ danh sách (quyền, phạm vi). */
  async updatePermissionGroup(permissionGroupId: string, payload: CreatePermissionGroupPayload): Promise<void> {
    await apiClient.put<void>(API_ROUTE.rbac.permissionGroup(permissionGroupId), payload);
  },

  async transferBranch(accountId: string, branchId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountBranch(accountId), { branchId });
  },
} as const;
