import { Can, RequirePermission } from "@/entities/permission";
import { ACCOUNTS_DIALOG, useAccountsPageController } from "@/modules/rbac/hooks/use-accounts-page-controller";
import { ACCOUNT_COMMAND_COPY } from "@/modules/rbac/model/account-rules";
import { AccountsTable } from "@/modules/rbac/ui/accounts-table";
import { AssignGroupDialog } from "@/modules/rbac/ui/assign-group-dialog";
import { ChangeRoleDialog } from "@/modules/rbac/ui/change-role-dialog";
import { CreateAccountDialog } from "@/modules/rbac/ui/create-account-dialog";
import { TransferBranchDialog } from "@/modules/rbac/ui/transfer-branch-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Users } from "lucide-react";

export function AccountsPage() {
  const controller = useAccountsPageController();
  const { accounts, selected, pendingCommand } = controller;

  return (
    <RequirePermission {...ACCESS_RULE.readAccount}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Tài khoản"
          description="Người dùng, vai trò, chi nhánh và trạng thái đăng nhập. Khoá tài khoản thu hồi mọi phiên ngay lập tức."
          actions={
            <Can {...ACCESS_RULE.createAccount}>
              <GlassButton onClick={controller.openCreateAccountDialog} icon={<Plus size={16} aria-hidden />}>
                Tạo tài khoản
              </GlassButton>
            </Can>
          }
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
          {accounts.data && accounts.data.items.length === 0 ? (
            <EmptyState
              icon={<Users size={28} aria-hidden />}
              title="Chưa có tài khoản nào"
              description="Tài khoản đầu tiên được tạo bởi quá trình khởi tạo hệ thống."
            />
          ) : null}
          {accounts.data && accounts.data.items.length > 0 ? (
            <>
              <AccountsTable
                rows={accounts.data.items}
                currentAccountId={controller.currentAccountId}
                onOpenDialog={controller.openDialog}
                onCommand={controller.requestCommand}
              />
              <Pagination
                page={accounts.data.page}
                totalPages={accounts.data.totalPages}
                totalItems={accounts.data.totalItems}
                onPageChange={controller.setPage}
              />
            </>
          ) : null}
        </GlassPanel>

        <CreateAccountDialog open={controller.createAccountDialogOpen} onClose={controller.closeCreateAccountDialog} />

        {/* Hộp thoại được dựng lại theo từng tài khoản (key), để form bên trong không giữ lựa chọn cũ. */}
        {selected === null ? null : (
          <>
            <AssignGroupDialog
              key={`assign-${selected.id}`}
              account={selected}
              open={controller.dialog === ACCOUNTS_DIALOG.assignGroup}
              onClose={controller.closeDialog}
            />
            <TransferBranchDialog
              key={`branch-${selected.id}`}
              account={selected}
              open={controller.dialog === ACCOUNTS_DIALOG.transferBranch}
              onClose={controller.closeDialog}
            />
            <ChangeRoleDialog
              key={`role-${selected.id}`}
              account={selected}
              open={controller.dialog === ACCOUNTS_DIALOG.changeRole}
              onClose={controller.closeDialog}
            />
          </>
        )}

        {pendingCommand === null ? null : (
          <ConfirmDialog
            open
            title={`${ACCOUNT_COMMAND_COPY[pendingCommand.command].title}: ${pendingCommand.account.fullName}`}
            description={ACCOUNT_COMMAND_COPY[pendingCommand.command].description}
            confirmLabel={ACCOUNT_COMMAND_COPY[pendingCommand.command].confirmLabel}
            tone={ACCOUNT_COMMAND_COPY[pendingCommand.command].tone}
            isPending={controller.isCommandPending}
            error={controller.commandError}
            onConfirm={controller.confirmCommand}
            onClose={controller.closeCommand}
          />
        )}
      </div>
    </RequirePermission>
  );
}
