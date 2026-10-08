import { accountKeys } from "@/entities/account";
import { branchApi, branchKeys, type CreateBranchPayload, type UpdateBranchPayload } from "@/entities/branch";
import { catalogKeys } from "@/entities/rbac-catalog";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Sau khi tạo/sửa chi nhánh, ba chỗ có thể đang giữ bản cũ: danh sách chi nhánh, danh mục tham chiếu
 * RBAC (dropdown chuyển chi nhánh, danh mục ở màn hình vai trò), và account - đổi tên/vô hiệu hoá một
 * chi nhánh đang được gán cho ai đó thì tên chi nhánh ở phiên đăng nhập (sidebar) và bảng Tài khoản
 * cũng phải làm mới theo, giống cách useRbacInvalidation đã làm khi chuyển chi nhánh cho một account.
 */
function useBranchInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: branchKeys.all }),
      queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
      queryClient.invalidateQueries({ queryKey: accountKeys.all }),
    ]);
  };
}

export function useCreateBranch() {
  const invalidate = useBranchInvalidation();
  return useMutation({
    mutationFn: (payload: CreateBranchPayload) => branchApi.createBranch(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateBranch(branchId: string) {
  const invalidate = useBranchInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateBranchPayload) => branchApi.updateBranch(branchId, payload),
    onSuccess: invalidate,
  });
}
