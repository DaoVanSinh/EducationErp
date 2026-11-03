import { useSelectedBranch } from "@/entities/branch";
import { usePermissions } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useEffect } from "react";

/**
 * Lựa chọn chi nhánh nằm trong localStorage nên có thể còn lại từ người dùng trước trên cùng máy. Người không có
 * quyền đổi chi nhánh thì không có cách bỏ lựa chọn đó, nên xoá ở đây để danh sách của họ không bị lọc oan.
 */
export function useBranchSelectionGuard(): void {
  const canSwitch = usePermissions().allows(ACCESS_RULE.readBranch);
  const { selectedBranchId, setSelectedBranchId } = useSelectedBranch();

  useEffect(() => {
    if (!canSwitch && selectedBranchId !== null) {
      setSelectedBranchId(null);
    }
  }, [canSwitch, selectedBranchId, setSelectedBranchId]);
}
