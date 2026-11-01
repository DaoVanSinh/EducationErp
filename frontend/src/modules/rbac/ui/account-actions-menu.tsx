import type { AccountSummary } from "@/entities/account";
import { Can } from "@/entities/permission";
import type { AccountCommand } from "@/modules/rbac/api/use-rbac-mutations";
import { ACCOUNTS_DIALOG, type AccountsDialogKind } from "@/modules/rbac/hooks/use-accounts-page-controller";
import { ACCOUNT_COMMAND_COPY } from "@/modules/rbac/model/account-rules";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useDisclosure } from "@/shared/lib/use-disclosure";
import { DropdownMenu } from "@/shared/ui/dropdown-menu";
import { GlassButton } from "@/shared/ui/glass-button";
import { MoreHorizontal } from "lucide-react";
import type { ReactNode } from "react";

export interface AccountActionsMenuProps {
  readonly account: AccountSummary;
  readonly isSelf: boolean;
  readonly commands: readonly AccountCommand[];
  readonly onOpenDialog: (kind: AccountsDialogKind, account: AccountSummary) => void;
  readonly onCommand: (account: AccountSummary, command: AccountCommand) => void;
}

/**
 * Mọi thao tác trên một tài khoản gom vào một menu - một dòng có tới sáu nút thì không đọc nổi. Ẩn hẳn
 * với người không có quyền sửa tài khoản cấp tổ chức (backend cũng chặn).
 */
export function AccountActionsMenu({ account, isSelf, commands, onOpenDialog, onCommand }: AccountActionsMenuProps) {
  const menu = useDisclosure();
  const run = (action: () => void) => () => {
    menu.onClose();
    action();
  };

  return (
    <Can {...ACCESS_RULE.updateAccount}>
      <div className="relative justify-self-start lg:justify-self-end">
        <GlassButton
          variant="secondary"
          size="sm"
          onClick={menu.onToggle}
          aria-haspopup="menu"
          aria-expanded={menu.open}
          icon={<MoreHorizontal size={14} aria-hidden />}
        >
          Thao tác
        </GlassButton>
        <DropdownMenu open={menu.open} onClose={menu.onClose} className="right-0 top-full mt-2">
          <Can {...ACCESS_RULE.readRole}>
            {isSelf ? null : (
              <MenuItem onSelect={run(() => onOpenDialog(ACCOUNTS_DIALOG.changeRole, account))}>Đổi vai trò</MenuItem>
            )}
            <MenuItem onSelect={run(() => onOpenDialog(ACCOUNTS_DIALOG.transferBranch, account))}>
              Chuyển chi nhánh
            </MenuItem>
          </Can>
          {commands.length > 0 ? <div className="my-1 h-px bg-slate-100" /> : null}
          {commands.map((command) => (
            <MenuItem
              key={command}
              danger={ACCOUNT_COMMAND_COPY[command].tone === "danger"}
              onSelect={run(() => onCommand(account, command))}
            >
              {ACCOUNT_COMMAND_COPY[command].label}
            </MenuItem>
          ))}
        </DropdownMenu>
      </div>
    </Can>
  );
}

function MenuItem({
  children,
  danger = false,
  onSelect,
}: {
  readonly children: ReactNode;
  readonly danger?: boolean;
  readonly onSelect: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={
        danger
          ? "flex w-full rounded-xl px-3 py-2 text-left text-sm text-rose-600 transition-colors hover:bg-rose-50 cursor-pointer"
          : "flex w-full rounded-xl px-3 py-2 text-left text-sm text-slate-700 transition-colors hover:bg-orange-50/70 cursor-pointer"
      }
    >
      {children}
    </button>
  );
}
