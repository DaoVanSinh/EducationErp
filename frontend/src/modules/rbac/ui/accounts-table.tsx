import { AccountStatusBadge, type AccountSummary, UserAvatar } from "@/entities/account";
import type { AccountCommand } from "@/modules/rbac/api/use-rbac-mutations";
import type { AccountsDialogKind } from "@/modules/rbac/hooks/use-accounts-page-controller";
import { availableCommands } from "@/modules/rbac/model/account-rules";
import { AccountActionsMenu } from "@/modules/rbac/ui/account-actions-menu";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { m } from "framer-motion";

export interface AccountsTableProps {
  readonly rows: readonly AccountSummary[];
  readonly currentAccountId: string | undefined;
  readonly onOpenDialog: (kind: AccountsDialogKind, account: AccountSummary) => void;
  readonly onCommand: (account: AccountSummary, command: AccountCommand) => void;
}

export function AccountsTable({ rows, currentAccountId, onOpenDialog, onCommand }: AccountsTableProps) {
  return (
    <ul className="flex flex-col gap-2.5 min-w-0 w-full">
      {rows.map((account, index) => (
        <m.li
          key={account.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          // Mỗi dòng kính (backdrop-filter + transform) là một stacking context riêng: z-50 của menu chỉ
          // có nghĩa bên trong dòng, nên dòng đang mở menu phải tự nổi lên trên các dòng phía sau.
          className="glass relative has-[[aria-expanded=true]]:z-20 grid grid-cols-1 items-center gap-3.5 rounded-2xl p-4 shadow-xs transition-all hover:border-orange-200/90 hover:shadow-sm lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.4fr)_auto] min-w-0 w-full"
        >
          <div className="flex min-w-0 items-center gap-3">
            <UserAvatar fullName={account.fullName} size="sm" />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-slate-800">
                {account.fullName}
                {account.id === currentAccountId ? <span className="ml-1.5 text-xs text-orange-600">(bạn)</span> : null}
              </p>
              <p className="truncate text-xs text-slate-500">{account.email}</p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <AccountStatusBadge status={account.status} />
            <Badge tone="accent">{account.roleCode}</Badge>
            {account.lastLogin === null ? <Badge tone="warning">Chưa kích hoạt</Badge> : null}
          </div>

          <div className="flex min-w-0 flex-col gap-0.5">
            <p className="truncate text-xs font-medium text-slate-700">
              {account.branchName ?? "Chưa thuộc chi nhánh nào"}
            </p>
            <p className="truncate text-xs text-slate-400">
              {account.groups.length === 0
                ? "Chưa vào nhóm người dùng nào"
                : account.groups.map((group) => group.name).join(", ")}
            </p>
          </div>

          <AccountActionsMenu
            account={account}
            isSelf={account.id === currentAccountId}
            commands={availableCommands(account, currentAccountId)}
            onOpenDialog={onOpenDialog}
            onCommand={onCommand}
          />
        </m.li>
      ))}
    </ul>
  );
}
