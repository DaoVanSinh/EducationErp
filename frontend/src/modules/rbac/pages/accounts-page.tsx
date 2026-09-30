import { RequirePermission } from "@/entities/permission";
import { ACCOUNTS_DIALOG, useAccountsPageController } from "@/modules/rbac/hooks/use-accounts-page-controller";
import { AccountsTable } from "@/modules/rbac/ui/accounts-table";
import { AssignGroupDialog } from "@/modules/rbac/ui/assign-group-dialog";
import { TransferBranchDialog } from "@/modules/rbac/ui/transfer-branch-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Users } from "lucide-react";

export function AccountsPage() {
  const {
    setPage,
    dialog,
    selected,
    accounts,
    openDialog,
    closeDialog,
  } = useAccountsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readAccount}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Tài khoản"
          description="Danh sách người dùng, nhóm quyền đang thuộc và chi nhánh đang làm việc."
        />

        {accounts.isError ? <ErrorNotice error={accounts.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {accounts.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {accounts.data ? (
            accounts.data.items.length === 0 ? (
              <EmptyState
                icon={<Users size={28} aria-hidden />}
                title="Chưa có tài khoản nào"
                description="Tài khoản đầu tiên được tạo bởi quá trình khởi tạo hệ thống."
              />
            ) : (
              <>
                <AccountsTable
                  rows={accounts.data.items}
                  onAssignGroup={(account) => openDialog(ACCOUNTS_DIALOG.assignGroup, account)}
                  onTransferBranch={(account) => openDialog(ACCOUNTS_DIALOG.transferBranch, account)}
                />
                <Pagination
                  page={accounts.data.page}
                  totalPages={accounts.data.totalPages}
                  totalItems={accounts.data.totalItems}
                  onPageChange={setPage}
                />
              </>
            )
          ) : null}
        </GlassPanel>

        {/* Hộp thoại được dựng lại theo từng tài khoản (key), để form bên trong không giữ lựa chọn cũ. */}
        {selected === null ? null : (
          <>
            <AssignGroupDialog
              key={`assign-${selected.id}`}
              account={selected}
              open={dialog === ACCOUNTS_DIALOG.assignGroup}
              onClose={closeDialog}
            />
            <TransferBranchDialog
              key={`branch-${selected.id}`}
              account={selected}
              open={dialog === ACCOUNTS_DIALOG.transferBranch}
              onClose={closeDialog}
            />
          </>
        )}
      </div>
    </RequirePermission>
  );
}
