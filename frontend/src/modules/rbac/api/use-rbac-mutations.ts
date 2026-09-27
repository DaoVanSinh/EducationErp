import { accountKeys } from "@/entities/account";
import { catalogKeys } from "@/entities/rbac-catalog";
import {
  rbacApi,
  type CreatePermissionGroupPayload,
  type CreateRolePayload,
} from "@/modules/rbac/api/rbac-api";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Sau mỗi thay đổi RBAC, backend đã xoá cache quyền trong Redis. Việc còn lại của frontend là bỏ bản
 * cũ trong TanStack Query: danh sách tài khoản, danh mục tham chiếu, và cả phiên hiện tại - vì người
 * vừa bị đổi quyền có thể chính là người đang bấm.
 */
function useRbacInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: accountKeys.all }),
      queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
    ]);
  };
}

export function useAssignGroup(accountId: string) {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (groupId: string) => rbacApi.assignGroup(accountId, groupId),
    onSuccess: invalidate,
  });
}

export function useTransferBranch(accountId: string) {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (branchId: string) => rbacApi.transferBranch(accountId, branchId),
    onSuccess: invalidate,
  });
}

export function useCreateRole() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (payload: CreateRolePayload) => rbacApi.createRole(payload),
    onSuccess: invalidate,
  });
}

export function useCreatePermissionGroup() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (payload: CreatePermissionGroupPayload) => rbacApi.createPermissionGroup(payload),
    onSuccess: invalidate,
  });
}
