import { useSelectedBranch } from "@/entities/branch/model/branch-context";
import { useState } from "react";

/**
 * Phân trang của một danh sách lọc theo chi nhánh đang chọn. Đổi chi nhánh thì về trang đầu, vì trang hiện tại có
 * thể vượt quá số trang của tập đã lọc. Chỉnh state ngay trong lúc render theo khuyến nghị của React, không dùng
 * effect. `branchId` là undefined khi xem toàn tổ chức, để không gửi tham số lên API.
 */
export function useBranchPaging() {
  const { selectedBranchId } = useSelectedBranch();
  const [page, setPage] = useState(0);
  const [previousBranchId, setPreviousBranchId] = useState(selectedBranchId);
  if (selectedBranchId !== previousBranchId) {
    setPreviousBranchId(selectedBranchId);
    setPage(0);
  }
  return { branchId: selectedBranchId ?? undefined, page, setPage };
}
