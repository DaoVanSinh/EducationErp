import { accountApi, accountKeys, type CreateAccountPayload } from "@/entities/account";
import { catalogKeys } from "@/entities/rbac-catalog";
import {
  rbacApi,
  type CreatePermissionGroupPayload,
  type CreateRolePayload,
  type UpdateRolePayload,
} from "@/modules/rbac/api/rbac-api";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Sau mỗi thay đổi RBAC, backend đã xoá cache quyền trong Redis. Việc còn lại của frontend là bỏ bản
 * cũ trong TanStack Query: danh sách tài khoản, danh mục tham chiếu, và cả phiên hiện tại - vì người
 * vừa bị đổi quyền có thể chính là người đang bấm (`accountKeys.all` bao gồm cả phiên).
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

export function useUpdateRole(roleId: string) {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateRolePayload) => rbacApi.updateRole(roleId, payload),
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

export function useUpdatePermissionGroup(permissionGroupId: string) {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (payload: CreatePermissionGroupPayload) =>
      rbacApi.updatePermissionGroup(permissionGroupId, payload),
    onSuccess: invalidate,
  });
}

export function useCreateAccount() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (payload: CreateAccountPayload) => accountApi.createAccount(payload),
    onSuccess: invalidate,
  });
}

export function useChangeAccountRole(accountId: string) {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: (roleId: string) => accountApi.changeRole(accountId, roleId),
    onSuccess: invalidate,
  });
}

/** Các lệnh một-nút trên một tài khoản, cần xác nhận trước khi gửi. */
export const ACCOUNT_COMMAND = {
  resendInvite: "RESEND_INVITE",
  revokeInvite: "REVOKE_INVITE",
  disable: "DISABLE",
  enable: "ENABLE",
  signOutEverywhere: "SIGN_OUT_EVERYWHERE",
} as const;

export type AccountCommand = (typeof ACCOUNT_COMMAND)[keyof typeof ACCOUNT_COMMAND];

const COMMAND_CALL: Record<AccountCommand, (accountId: string) => Promise<void>> = {
  [ACCOUNT_COMMAND.resendInvite]: (accountId) => accountApi.resendInvite(accountId),
  [ACCOUNT_COMMAND.revokeInvite]: (accountId) => accountApi.revokeInvite(accountId),
  [ACCOUNT_COMMAND.disable]: (accountId) => accountApi.disable(accountId),
  [ACCOUNT_COMMAND.enable]: (accountId) => accountApi.enable(accountId),
  [ACCOUNT_COMMAND.signOutEverywhere]: (accountId) => accountApi.signOutEverywhere(accountId),
};

export function useAccountCommand() {
  const invalidate = useRbacInvalidation();
  return useMutation({
    mutationFn: ({ accountId, command }: { accountId: string; command: AccountCommand }) =>
      COMMAND_CALL[command](accountId),
    onSuccess: invalidate,
  });
}
