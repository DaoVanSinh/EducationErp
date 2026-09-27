import { AccountStatusBadge, type AccountSummary, UserAvatar } from "@/entities/account";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Building2, UserPlus } from "lucide-react";

export interface AccountsTableProps {
  readonly rows: readonly AccountSummary[];
  readonly onAssignGroup: (account: AccountSummary) => void;
  readonly onTransferBranch: (account: AccountSummary) => void;
}

/**
 * Bảng ở màn hình rộng, danh sách thẻ ở màn hình hẹp - cùng một dữ liệu, dựng bằng grid nên không phải
 * viết hai lần phần nội dung.
 */
export function AccountsTable({ rows, onAssignGroup, onTransferBranch }: AccountsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((account, index) => (
        <m.li
          key={account.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.4fr)_auto]"
        >
          <div className="flex min-w-0 items-center gap-3">
            <UserAvatar fullName={account.fullName} size="sm" />
            <div className="min-w-0">
              <p className="truncate text-sm text-mist-100">{account.fullName}</p>
              <p className="truncate text-xs text-mist-500">{account.email}</p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <AccountStatusBadge status={account.status} />
            <Badge tone="accent">{account.roleCode}</Badge>
          </div>

          <div className="flex min-w-0 flex-col gap-1">
            <p className="truncate text-xs text-mist-400">
              {account.branchName ?? "Chưa thuộc chi nhánh nào"}
            </p>
            <p className="truncate text-xs text-mist-500">
              {account.groups.length === 0
                ? "Chưa vào nhóm nào"
                : account.groups.map((group) => group.name).join(", ")}
            </p>
          </div>

          {/* Hai hộp thoại đều nạp danh mục nhóm quyền/chi nhánh, mà endpoint danh mục đòi ROLE:READ.
              Thiếu quyền đó thì nút vẫn bấm được nhưng ô chọn sẽ 403, nên cổng phải hỏi đủ cả hai. */}
          <Can {...ACCESS_RULE.updateAccount}>
            <Can {...ACCESS_RULE.readRole}>
              <div className="flex items-center gap-2 justify-self-start lg:justify-self-end">
                <GlassButton
                  variant="secondary"
                  size="sm"
                  onClick={() => onAssignGroup(account)}
                  icon={<UserPlus size={14} aria-hidden />}
                >
                  Nhóm
                </GlassButton>
                <GlassButton
                  variant="secondary"
                  size="sm"
                  onClick={() => onTransferBranch(account)}
                  icon={<Building2 size={14} aria-hidden />}
                >
                  Chi nhánh
                </GlassButton>
              </div>
            </Can>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
