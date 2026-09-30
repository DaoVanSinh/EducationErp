import { useAccounts, type AccountSummary } from "@/entities/account";
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
  const accounts = useAccounts(page, DEFAULT_PAGE_SIZE);

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
