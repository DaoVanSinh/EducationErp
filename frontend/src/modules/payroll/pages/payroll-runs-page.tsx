import { Can, RequirePermission } from "@/entities/permission";
import { usePayrollRunsPageController } from "@/modules/payroll/hooks/use-payroll-runs-page-controller";
import { CreatePayrollRunDialog } from "@/modules/payroll/ui/create-payroll-run-dialog";
import { PayrollRunsTable } from "@/modules/payroll/ui/payroll-runs-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Wallet } from "lucide-react";

export function PayrollRunsPage() {
  const controller = usePayrollRunsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Kỳ lương"
          description="Kế toán tạo kỳ lương, chủ trung tâm duyệt rồi khoá."
          actions={
            <Can {...ACCESS_RULE.createPayroll}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Kỳ lương mới
              </GlassButton>
            </Can>
          }
        />

        {controller.runs.isError ? <ErrorNotice error={controller.runs.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {controller.runs.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.runs.data ? (
            controller.runs.data.items.length === 0 ? (
              <EmptyState icon={<Wallet size={28} aria-hidden />} title="Chưa có kỳ lương nào"
                description="Tạo kỳ lương đầu tiên để bắt đầu chốt lương." />
            ) : (
              <>
                <PayrollRunsTable rows={controller.runs.data.items} />
                <Pagination
                  page={controller.runs.data.page}
                  totalPages={controller.runs.data.totalPages}
                  totalItems={controller.runs.data.totalItems}
                  onPageChange={controller.setPage}
                  itemLabel="kỳ lương"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreatePayrollRunDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />
      </div>
    </RequirePermission>
  );
}
