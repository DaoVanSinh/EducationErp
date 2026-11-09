import { useAccounts, useSession, type AccountSummary } from "@/entities/account";
import { useSelectedBranch } from "@/entities/branch";
import { usePermissions } from "@/entities/permission";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useAccountCommand, type AccountCommand } from "@/modules/rbac/api/use-rbac-mutations";
import { FALLBACK_ROLE_FILTER_OPTIONS } from "@/modules/rbac/model/account-rules";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from "@/shared/constants/query-config";
import { useDebouncedValue } from "@/shared/lib/use-debounced-value";
import { useCallback, useState } from "react";

export const ACCOUNTS_DIALOG = {
  none: "NONE",
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
  const [roleCode, setRoleCode] = useState("");
  const [status, setStatus] = useState("");
  const [queryInput, setQueryInput] = useState("");
  const query = useDebouncedValue(queryInput.trim(), SEARCH_DEBOUNCE_MS);
  const { selectedBranchId } = useSelectedBranch();
  const permissions = usePermissions();
  const canManageOrganization = permissions.allows(ACCESS_RULE.updateAccount);
  const catalog = useRbacCatalog({ enabled: permissions.allows(ACCESS_RULE.readRole) });
  const accounts = useAccounts(page, DEFAULT_PAGE_SIZE, { branchId: selectedBranchId, roleCode, status, query });
  const session = useSession();
  const accountCommand = useAccountCommand();

  // Đổi chi nhánh hoặc bộ lọc thì về trang đầu - trang hiện tại có thể vượt quá số trang của tập đã lọc.
  // Chỉnh state ngay trong lúc render (theo đúng khuyến nghị của React) thay vì dùng effect, để không
  // kích hoạt thêm một lượt render-commit-effect chỉ để đặt lại một state dẫn xuất từ prop đổi.
  const filterKey = [selectedBranchId, roleCode, status, query].join("|");
  const [previousFilterKey, setPreviousFilterKey] = useState(filterKey);
  if (filterKey !== previousFilterKey) {
    setPreviousFilterKey(filterKey);
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
    canManageOrganization,
    roleOptions: catalog.data?.roles ?? FALLBACK_ROLE_FILTER_OPTIONS,
    roleCode,
    setRoleCode,
    status,
    setStatus,
    queryInput,
    setQueryInput,
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
