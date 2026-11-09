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

type Controller = ReturnType<typeof usePayrollRunsPageController>;

const STEPS = [
  { title: "1. Nháp", text: "Kế toán tạo kỳ cho một tháng. Hệ thống tính phiếu từ mức lương và điểm danh; kế toán thêm thưởng/điều chỉnh nếu có." },
  { title: "2. Chờ duyệt", text: "Sau khi hết tháng, kế toán gửi duyệt. Hệ thống tính lại lần cuối và khoá chỉnh sửa." },
  { title: "3. Đã duyệt", text: "Chủ trung tâm duyệt thì số liệu được chốt và giáo viên thấy phiếu lương. Từ chối thì kỳ về Nháp kèm lý do." },
] as const;

export function PayrollRunsPage() {
  const controller = usePayrollRunsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Kỳ lương"
          description="Mỗi kỳ là trọn một tháng dương lịch, tính cho toàn bộ giáo viên."
          actions={
            <Can {...ACCESS_RULE.createPayroll}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Kỳ lương mới
              </GlassButton>
            </Can>
          }
        />

        <ol className="grid grid-cols-1 gap-3 lg:grid-cols-3">
          {STEPS.map((step) => (
            <li key={step.title} className="glass rounded-2xl p-3">
              <p className="text-sm font-medium text-mist-100">{step.title}</p>
              <p className="text-xs text-mist-400">{step.text}</p>
            </li>
          ))}
        </ol>

        {controller.runs.isError ? <ErrorNotice error={controller.runs.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <PayrollRunsContent controller={controller} />
        </GlassPanel>

        <CreatePayrollRunDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />
      </div>
    </RequirePermission>
  );
}

function PayrollRunsContent({ controller }: { readonly controller: Controller }) {
  if (controller.runs.isPending) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </div>
    );
  }
  if (!controller.runs.data) {
    return null;
  }
  if (controller.runs.data.items.length === 0) {
    return (
      <EmptyState
        icon={<Wallet size={28} aria-hidden />}
        title="Chưa có kỳ lương nào"
        description="Tạo kỳ lương đầu tiên để bắt đầu chốt lương."
      />
    );
  }
  return (
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
  );
}
