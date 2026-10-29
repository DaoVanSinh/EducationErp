import { useAccounts, useSession, type AccountSummary } from "@/entities/account";
import { useSelectedBranch } from "@/entities/branch";
import { useAccountCommand, type AccountCommand } from "@/modules/rbac/api/use-rbac-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

export const ACCOUNTS_DIALOG = {
  none: "NONE",
  assignGroup: "ASSIGN_GROUP",
  transferBranch: "TRANSFER_BRANCH",
  changeRole: "CHANGE_ROLE",
} as const;

export type AccountsDialogKind = (typeof ACCOUNTS_DIALOG)[keyof typeof ACCOUNTS_DIALOG];

export interface PendingCommand {
  readonly account: AccountSummary;
  readonly command: AccountCommand;
}

export function useAccountsPageController() {
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<AccountsDialogKind>(ACCOUNTS_DIALOG.none);
  const [selected, setSelected] = useState<AccountSummary | null>(null);
  const [pendingCommand, setPendingCommand] = useState<PendingCommand | null>(null);
  const [createAccountDialogOpen, setCreateAccountDialogOpen] = useState(false);
  const { selectedBranchId } = useSelectedBranch();
  const accounts = useAccounts(page, DEFAULT_PAGE_SIZE, selectedBranchId);
  const session = useSession();
  const accountCommand = useAccountCommand();

  // Đổi chi nhánh đang xem thì về trang đầu - trang hiện tại có thể vượt quá số trang của tập đã lọc.
  // Chỉnh state ngay trong lúc render (theo đúng khuyến nghị của React) thay vì dùng effect, để không
  // kích hoạt thêm một lượt render-commit-effect chỉ để đặt lại một state dẫn xuất từ prop đổi.
  const [previousBranchId, setPreviousBranchId] = useState(selectedBranchId);
  if (selectedBranchId !== previousBranchId) {
    setPreviousBranchId(selectedBranchId);
    setPage(0);
  }

  const openDialog = useCallback((kind: AccountsDialogKind, account: AccountSummary) => {
    setSelected(account);
    setDialog(kind);
  }, []);

  const requestCommand = useCallback(
    (account: AccountSummary, command: AccountCommand) => {
      accountCommand.reset();
      setPendingCommand({ account, command });
    },
    [accountCommand],
  );

  const confirmCommand = useCallback(() => {
    if (pendingCommand === null) {
      return;
    }
    accountCommand.mutate(
      { accountId: pendingCommand.account.id, command: pendingCommand.command },
      { onSuccess: () => setPendingCommand(null) },
    );
  }, [accountCommand, pendingCommand]);

  return {
    page,
    setPage,
    dialog,
    selected,
    accounts,
    currentAccountId: session.data?.accountId,
    openDialog,
    closeDialog: () => setDialog(ACCOUNTS_DIALOG.none),
    createAccountDialogOpen,
    openCreateAccountDialog: () => setCreateAccountDialogOpen(true),
    closeCreateAccountDialog: () => setCreateAccountDialogOpen(false),
    pendingCommand,
    requestCommand,
    confirmCommand,
    closeCommand: () => setPendingCommand(null),
    commandError: accountCommand.error,
    isCommandPending: accountCommand.isPending,
  };
}
