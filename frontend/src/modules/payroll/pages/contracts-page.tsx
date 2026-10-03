import { Can, RequirePermission } from "@/entities/permission";
import { useContractsPageController } from "@/modules/payroll/hooks/use-contracts-page-controller";
import { ContractsTable } from "@/modules/payroll/ui/contracts-table";
import { CreateContractDialog } from "@/modules/payroll/ui/create-contract-dialog";
import { EditContractDialog } from "@/modules/payroll/ui/edit-contract-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { FileText, Plus } from "lucide-react";

export function ContractsPage() {
  const controller = useContractsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Hợp đồng lao động"
          description="Áp dụng cho mọi nhân viên có hợp đồng - giáo viên và nhân viên hành chính."
          actions={
            <Can {...ACCESS_RULE.createPayroll}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Hợp đồng mới
              </GlassButton>
            </Can>
          }
        />

        {controller.contracts.isError ? <ErrorNotice error={controller.contracts.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {controller.contracts.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.contracts.data ? (
            controller.contracts.data.items.length === 0 ? (
              <EmptyState icon={<FileText size={28} aria-hidden />} title="Chưa có hợp đồng nào"
                description="Tạo hợp đồng đầu tiên để bắt đầu chốt lương." />
            ) : (
              <>
                <ContractsTable
                  rows={controller.contracts.data.items}
                  onEdit={controller.startEditing}
                  onTerminate={controller.onTerminate}
                  onDownload={controller.onDownload}
                  isTerminating={controller.isTerminating}
                />
                <Pagination
                  page={controller.contracts.data.page}
                  totalPages={controller.contracts.data.totalPages}
                  totalItems={controller.contracts.data.totalItems}
                  onPageChange={controller.setPage}
                  itemLabel="hợp đồng"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateContractDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />

        {controller.editing === null ? null : (
          <EditContractDialog key={controller.editing.id} contract={controller.editing} open
            onClose={controller.stopEditing} />
        )}
      </div>
    </RequirePermission>
  );
}
