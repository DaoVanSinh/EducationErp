import { useBranches, type BranchSummary } from "@/entities/branch";
import { Can, RequirePermission } from "@/entities/permission";
import { BranchesTable } from "@/modules/organization/ui/branches-table";
import { CreateBranchDialog } from "@/modules/organization/ui/create-branch-dialog";
import { EditBranchDialog } from "@/modules/organization/ui/edit-branch-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Building2, Plus } from "lucide-react";
import { useState } from "react";

export function BranchesPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<BranchSummary | null>(null);
  const branches = useBranches(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readBranch}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Chi nhánh"
          description="Danh sách chi nhánh trong hệ thống. Chi nhánh chỉ được vô hiệu hoá, không xoá."
          actions={
            <Can {...ACCESS_RULE.createBranch}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Chi nhánh mới
              </GlassButton>
            </Can>
          }
        />

        {branches.isError ? <ErrorNotice error={branches.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {branches.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {branches.data ? (
            branches.data.items.length === 0 ? (
              <EmptyState
                icon={<Building2 size={28} aria-hidden />}
                title="Chưa có chi nhánh nào"
                description="Tạo chi nhánh đầu tiên để bắt đầu gán tài khoản vào đó."
              />
            ) : (
              <>
                <BranchesTable rows={branches.data.items} onEdit={setEditing} />
                <Pagination
                  page={branches.data.page}
                  totalPages={branches.data.totalPages}
                  totalItems={branches.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="chi nhánh"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateBranchDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {/* Hộp thoại được dựng lại theo từng chi nhánh (key), để form bên trong không giữ giá trị cũ. */}
        {editing === null ? null : (
          <EditBranchDialog
            key={editing.id}
            branch={editing}
            open={editing !== null}
            onClose={() => setEditing(null)}
          />
        )}
      </div>
    </RequirePermission>
  );
}
