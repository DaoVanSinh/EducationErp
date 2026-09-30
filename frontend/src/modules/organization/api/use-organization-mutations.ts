import { branchApi, branchKeys, type CreateBranchPayload, type UpdateBranchPayload } from "@/entities/branch";
import { catalogKeys } from "@/entities/rbac-catalog";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Sau khi tạo/sửa chi nhánh, danh sách chi nhánh và danh mục tham chiếu RBAC (dropdown chuyển chi
 * nhánh, danh mục ở màn hình vai trò) đều có thể đang giữ bản cũ - bỏ cache cả hai.
 */
function useBranchInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: branchKeys.all }),
      queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
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
