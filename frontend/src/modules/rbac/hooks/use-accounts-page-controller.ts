import { useAccounts, type AccountSummary } from "@/entities/account";
import { useSelectedBranch } from "@/entities/branch";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useState } from "react";

export const ACCOUNTS_DIALOG = {
  none: "NONE",
  assignGroup: "ASSIGN_GROUP",
  transferBranch: "TRANSFER_BRANCH",
} as const;

export type AccountsDialogKind = (typeof ACCOUNTS_DIALOG)[keyof typeof ACCOUNTS_DIALOG];

export function useAccountsPageController() {
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<AccountsDialogKind>(ACCOUNTS_DIALOG.none);
  const [selected, setSelected] = useState<AccountSummary | null>(null);
  const { selectedBranchId } = useSelectedBranch();
  const accounts = useAccounts(page, DEFAULT_PAGE_SIZE, selectedBranchId);

  // Đổi chi nhánh đang xem thì về trang đầu - trang hiện tại có thể vượt quá số trang của tập đã lọc.
  // Chỉnh state ngay trong lúc render (theo đúng khuyến nghị của React) thay vì dùng effect, để không
  // kích hoạt thêm một lượt render-commit-effect chỉ để đặt lại một state dẫn xuất từ prop đổi.
  const [previousBranchId, setPreviousBranchId] = useState(selectedBranchId);
  if (selectedBranchId !== previousBranchId) {
    setPreviousBranchId(selectedBranchId);
    setPage(0);
  }

  const openDialog = (kind: AccountsDialogKind, account: AccountSummary) => {
    setSelected(account);
    setDialog(kind);
  };

  const closeDialog = () => setDialog(ACCOUNTS_DIALOG.none);

  return {
    page,
    setPage,
    dialog,
    selected,
    accounts,
    openDialog,
    closeDialog,
  };
}
